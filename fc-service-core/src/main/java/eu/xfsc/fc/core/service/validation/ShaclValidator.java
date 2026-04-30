package eu.xfsc.fc.core.service.validation;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.util.ModelPrinter;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Validates RDF assets against SHACL shapes using Apache Jena and TopBraid SHACL.
 *
 * <p>Supports all RDF asset cases:
 * <ul>
 *   <li>Case A — Loire JWT: unwrapped via {@link LoireJwtParser}, then parsed as JSON-LD</li>
 *   <li>Case B — Credential JSON-LD: parsed directly as JSON-LD 1.1</li>
 *   <li>Case C — Non-credential RDF: parsed with format detected from content-type or content</li>
 * </ul>
 *
 * <p>For multi-asset SHACL, all asset models are merged before validation.</p>
 */
@Slf4j
@Service
public class ShaclValidator {

  private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";
  private static final Map<String, ValidationViolation.SeverityEnum> SHACL_SEVERITY_MAP = Map.of(
      SHACL_NS + "Violation", ValidationViolation.SeverityEnum.VIOLATION,
      SHACL_NS + "Warning", ValidationViolation.SeverityEnum.WARNING,
      SHACL_NS + "Info", ValidationViolation.SeverityEnum.INFO
  );

  private final FileStore fileStore;
  private final DocumentLoader documentLoader;
  private final LoireJwtParser loireJwtParser;

  @Value("${federated-catalogue.validation.shacl.timeout-seconds:10}")
  private int shaclTimeoutSeconds;

  @Value("${federated-catalogue.validation.shacl.pool-size:4}")
  private int shaclPoolSize;

  private StreamManager streamManager;
  // Application-scoped pool — avoids per-call thread creation; threads are reused across requests.
  private ExecutorService shaclExecutor;

  /** Creates the validator with context-cache file store, JSON-LD document loader, and JWT unwrapper. */
  public ShaclValidator(
      @Qualifier("contextCacheFileStore") FileStore fileStore,
      DocumentLoader documentLoader,
      LoireJwtParser loireJwtParser) {
    this.fileStore = fileStore;
    this.documentLoader = documentLoader;
    this.loireJwtParser = loireJwtParser;
  }

  @PostConstruct
  void init() {
    SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
    loader.set("file", documentLoader);
    loader.set("http", documentLoader);
    loader.set("https", documentLoader);
    StreamManager clone = StreamManager.get().clone();
    clone.clearLocators();
    clone.addLocator(new CachingLocator(fileStore));
    streamManager = clone;
    shaclExecutor = Executors.newFixedThreadPool(shaclPoolSize);
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    shaclExecutor.shutdown();
    if (!shaclExecutor.awaitTermination(shaclTimeoutSeconds + 5L, TimeUnit.SECONDS)) {
      shaclExecutor.shutdownNow();
    }
  }

  /**
   * Validates one or more RDF assets against the given merged SHACL shapes model.
   *
   * <p>All asset content is combined into a single data graph before validation runs.
   * Each asset's content is individually unwrapped (JWT) and parsed (RDF) using the
   * format determined by its content-type or content inspection.</p>
   *
   * @param assets      RDF assets to validate; must all have non-null content
   * @param shapesModel merged SHACL shapes graph built from the requested schema(s)
   * @return validation report with conforms flag, violations, and raw Turtle report
   */
  public ValidationReport validate(List<AssetMetadata> assets, Model shapesModel) {
    Model dataModel = buildMergedDataModel(assets);
    return runValidationWithTimeout(dataModel, shapesModel);
  }

  /**
   * Parses a SHACL shapes ContentAccessor (Turtle) into a Jena Model.
   *
   * @param shape ContentAccessor containing a SHACL Turtle document
   * @return parsed Jena Model representing the shapes graph
   */
  public Model parseShapeModel(ContentAccessor shape) {
    try {
      Model model = ModelFactory.createDefaultModel();
      RDFParser.create()
          .streamManager(streamManager)
          .source(shape.getContentAsStream())
          .lang(Lang.TURTLE)
          .parse(model);
      return model;
    } catch (RiotException e) {
      throw new ClientException("Invalid SHACL shape: " + e.getMessage(), e);
    }
  }

  private Model buildMergedDataModel(List<AssetMetadata> assets) {
    Model merged = ModelFactory.createDefaultModel();
    for (AssetMetadata asset : assets) {
      merged.add(parseAssetToModel(asset));
    }
    return merged;
  }

  private Model parseAssetToModel(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content == null) {
      throw new ClientException(
          "Asset " + asset.getId() + " is not an RDF asset and cannot be validated with SHACL");
    }
    String rawContent = content.getContentAsString().strip();
    // Case A — Loire JWT: starts with Base64url header "eyJ"
    if (rawContent.startsWith("eyJ")) {
      ContentAccessor unwrapped = loireJwtParser.unwrap(content);
      return parseRdfContent(unwrapped, Lang.JSONLD11);
    }
    Lang lang = detectRdfLang(asset.getContentType(), rawContent);
    return parseRdfContent(content, lang);
  }

  private Lang detectRdfLang(String contentType, String rawContent) {
    if (contentType != null) {
      if (contentType.contains("text/turtle")) {
        return Lang.TURTLE;
      }
      if (contentType.contains("n-triples")) {
        return Lang.NT;
      }
      if (contentType.contains("rdf+xml")) {
        return Lang.RDFXML;
      }
      if (contentType.contains("application/vc+ld+json") || contentType.contains("application/vp+ld+json")
          || contentType.contains("application/ld+json") || contentType.contains("application/json")) {
        return Lang.JSONLD11;
      }
    }
    // Content inspection fallback for application/ld+json or unknown
    if (rawContent.startsWith("{")) {
      return Lang.JSONLD11;
    }
    if (rawContent.startsWith("@prefix") || rawContent.startsWith("@base")) {
      return Lang.TURTLE;
    }
    if (rawContent.startsWith("<?xml") || rawContent.startsWith("<rdf:RDF")) {
      return Lang.RDFXML;
    }
    return Lang.NT;
  }

  private Model parseRdfContent(ContentAccessor content, Lang lang) {
    try {
      Model model = ModelFactory.createDefaultModel();
      RDFParser.create()
          .streamManager(streamManager)
          .source(content.getContentAsStream())
          .lang(lang)
          .parse(model);
      return model;
    } catch (RiotException e) {
      throw new ClientException("Invalid RDF asset content: " + e.getMessage(), e);
    }
  }

  private ValidationReport runValidationWithTimeout(Model dataModel, Model shapesModel) {
    Future<Resource> future = shaclExecutor.submit(
        () -> ValidationUtil.validateModel(dataModel, shapesModel, true));
    try {
      Resource reportResource = future.get(shaclTimeoutSeconds, TimeUnit.SECONDS);
      return toValidationReport(reportResource);
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

  private ValidationReport toValidationReport(Resource reportResource) {
    boolean conforms = reportResource.getProperty(SH.conforms).getBoolean();
    ValidationReport report = new ValidationReport();
    report.setConforms(conforms);

    if (conforms) {
      report.setViolations(List.of());
      return report;
    }

    List<ValidationViolation> violations = new ArrayList<>();
    StmtIterator results = reportResource.getModel().listStatements(null, SH.result, (RDFNode) null);
    while (results.hasNext()) {
      Resource result = results.next().getObject().asResource();
      ValidationViolation violation = new ValidationViolation();
      violation.setFocusNode(getStringProperty(result, SH.focusNode));
      violation.setResultPath(getStringProperty(result, SH.resultPath));
      violation.setMessage(getStringProperty(result, SH.resultMessage));
      String severityUri = getStringProperty(result, SH.resultSeverity);
      violation.setSeverity(SHACL_SEVERITY_MAP.getOrDefault(severityUri, ValidationViolation.SeverityEnum.VIOLATION));
      violation.setSourceShape(getStringProperty(result, SH.sourceShape));
      violations.add(violation);
    }
    report.setViolations(violations);
    report.setRawReport(ModelPrinter.get().print(reportResource.getModel()));
    return report;
  }

  private String getStringProperty(Resource resource, Property property) {
    var stmt = resource.getProperty(property);
    if (stmt == null) {
      return null;
    }
    RDFNode node = stmt.getObject();
    if (node.isLiteral()) {
      return node.asLiteral().getString();
    }
    if (node.isResource()) {
      return node.asResource().getURI();
    }
    return null;
  }
}
