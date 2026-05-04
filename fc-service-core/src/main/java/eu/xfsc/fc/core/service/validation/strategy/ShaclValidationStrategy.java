package eu.xfsc.fc.core.service.validation.strategy;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.service.validation.report.ValidationReportFactory;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.validation.ValidationUtil;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * {@link ValidationStrategy} implementation for SHACL validation of RDF assets.
 *
 * <p>Supports all RDF serializations — Turtle, N-Triples, JSON-LD, RDF/XML — including
 * Loire JWT-wrapped credentials. For multi-asset requests, all asset models are merged
 * before validation. All shape models are also merged when multiple schemas are provided.</p>
 *
 * <p>Parsing is delegated to {@link RdfAssetParser}; this strategy owns only the
 * TopBraid SHACL execution and result mapping.</p>
 */
@Slf4j
@Service
public class ShaclValidationStrategy implements ValidationStrategy {

  private final RdfAssetParser rdfAssetParser;

  @Value("${federated-catalogue.validation.shacl.timeout-seconds:10}")
  private int shaclTimeoutSeconds;

  @Value("${federated-catalogue.validation.shacl.pool-size:4}")
  private int shaclPoolSize;

  // Application-scoped pool — avoids per-call thread creation; threads are reused across requests.
  private ExecutorService shaclExecutor;

  public ShaclValidationStrategy(RdfAssetParser rdfAssetParser) {
    this.rdfAssetParser = rdfAssetParser;
  }

  @PostConstruct
  void init() {
    shaclExecutor = Executors.newFixedThreadPool(shaclPoolSize);
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    shaclExecutor.shutdown();
    if (!shaclExecutor.awaitTermination(shaclTimeoutSeconds + 5L, TimeUnit.SECONDS)) {
      shaclExecutor.shutdownNow();
    }
  }

  @Override
  public ValidatorType type() {
    return ValidatorType.SHACL;
  }

  @Override
  public String moduleType() {
    return SchemaModuleType.SHACL;
  }

  /** Returns {@code true} for any asset that has RDF content (content accessor is non-null). */
  @Override
  public boolean appliesTo(AssetMetadata asset) {
    return asset.getContentAccessor() != null;
  }

  @Override
  public boolean acceptsSchema(SchemaRecord record) {
    return record.type() == SchemaType.SHAPE;
  }

  /**
   * Validates one or more RDF assets against one or more SHACL shape graphs.
   *
   * <p>All asset models are merged into a single data graph before validation.
   * All schema ContentAccessors are parsed and merged into a single shapes graph.</p>
   *
   * @param assets   RDF assets; each must have a non-null content accessor
   * @param schemas  SHACL shape documents (Turtle); must not be empty
   * @return validation report with conforms flag, violations, and raw Turtle report
   */
  @Override
  public ValidationReport validate(List<AssetMetadata> assets, List<ContentAccessor> schemas) {
    Model shapesModel = buildMergedShapesModel(schemas);
    Model dataModel = buildMergedDataModel(assets);
    return runValidationWithTimeout(dataModel, shapesModel);
  }

  private Model buildMergedShapesModel(List<ContentAccessor> schemas) {
    Model merged = ModelFactory.createDefaultModel();
    for (ContentAccessor schema : schemas) {
      merged.add(rdfAssetParser.parseShape(schema));
    }
    return merged;
  }

  private Model buildMergedDataModel(List<AssetMetadata> assets) {
    Model merged = ModelFactory.createDefaultModel();
    for (AssetMetadata asset : assets) {
      merged.add(rdfAssetParser.parse(asset));
    }
    return merged;
  }

  private ValidationReport runValidationWithTimeout(Model dataModel, Model shapesModel) {
    Future<Resource> future = shaclExecutor.submit(
        () -> ValidationUtil.validateModel(dataModel, shapesModel, true));
    try {
      Resource reportResource = future.get(shaclTimeoutSeconds, TimeUnit.SECONDS);
      return ValidationReportFactory.fromShacl(reportResource);
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      throw new TimeoutException(
          "SHACL validation timed out after " + shaclTimeoutSeconds + " seconds");
    } catch (ExecutionException e) {
      throw new ServerException(
          "SHACL validation failed: " + e.getCause().getMessage(), e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServerException("SHACL validation interrupted", e);
    }
  }

}
