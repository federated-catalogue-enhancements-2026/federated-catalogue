package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.SingleAssetValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link AssetValidationService}.
 *
 * <p>Orchestrates on-demand validation of stored assets against stored schemas.
 * Handles module toggles, schema resolution, and result storage via {@link ValidationResultStore}.</p>
 *
 * <p>Cross-paradigm validation: JSON Schema applies to RDF assets serialized as JSON-LD;
 * XML Schema applies to RDF assets serialized as RDF/XML. When an RDF asset is validated,
 * all applicable schema types are dispatched, each storing an independent
 * {@link eu.xfsc.fc.core.dao.validation.ValidationResult}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetValidationServiceImpl implements AssetValidationService {

  private static final String JSON_LD_CONTENT_PREFIX = "{";
  private static final String RDF_XML_CONTENT_PREFIX_1 = "<?xml";
  private static final String RDF_XML_CONTENT_PREFIX_2 = "<rdf:RDF";
  private static final String MEDIA_TYPE_SCHEMA_JSON = "application/schema+json";

  @Value("${federated-catalogue.validation.max-assets-per-request:20}")
  private int maxAssetsPerRequest;

  private final AssetStore assetStore;
  @Qualifier("assetFileStore")
  private final FileStore fileStore;
  private final SchemaStore schemaStore;
  private final SchemaModuleConfigService moduleConfig;
  private final ShaclValidator shaclValidator;
  private final JsonSchemaValidator jsonSchemaValidator;
  private final XmlSchemaValidator xmlSchemaValidator;
  private final ValidationResultStore validationResultStore;

  @Override
  @Transactional
  public ValidationResponse validateAsset(String assetId, SingleAssetValidationRequest request) {
    log.debug("validateAsset.enter; assetId={}", assetId);
    AssetMetadata asset = assetStore.getById(assetId);
    boolean isRdf = asset.getContentAccessor() != null;

    if (isRdf) {
      return validateRdfAsset(asset, request);
    }
    return validateNonRdfAsset(asset, request);
  }

  @Override
  @Transactional
  public ValidationResponse validateAssets(ValidationRequest request) {
    log.debug("validateAssets.enter; assetIds={}", request.getAssetIds());
    List<String> assetIds = request.getAssetIds();
    if (assetIds == null || assetIds.isEmpty()) {
      throw new ClientException("assetIds must contain at least one asset ID");
    }
    if (assetIds.size() > maxAssetsPerRequest) {
      throw new ClientException(
          "Too many assets in request: " + assetIds.size() + " (maximum: " + maxAssetsPerRequest + ")");
    }
    requireModuleEnabled(SchemaModuleType.SHACL);

    List<AssetMetadata> assets = new ArrayList<>();
    for (String id : assetIds) {
      AssetMetadata asset = assetStore.getById(id);
      if (asset.getContentAccessor() == null) {
        throw new VerificationException(
            "Asset " + id + " is not an RDF asset. Multi-asset validation only supports RDF assets for SHACL.");
      }
      assets.add(asset);
    }

    List<String> schemaIds = request.getSchemaIds();
    Boolean validateAll = request.getValidateAgainstAllSchemas();
    SchemaResolutionResult schemas = resolveShaclSchemas(schemaIds, validateAll);

    ValidationReport report = shaclValidator.validate(assets, schemas.shapesModel());
    Instant validatedAt = Instant.now();
    Long resultId = storeResult(assetIds, schemas.validatorIds(), ValidatorType.SHACL, report, validatedAt);

    log.debug("validateAssets.exit; conforms={}", report.getConforms());
    return buildResponse(assetIds, schemas.schemaIds(), report, List.of(resultId), validatedAt);
  }

  // --- RDF asset routing ---

  private ValidationResponse validateRdfAsset(AssetMetadata asset, SingleAssetValidationRequest request) {
    List<String> schemaIds = request != null ? request.getSchemaIds() : null;
    Boolean validateAll = request != null ? request.getValidateAgainstAllSchemas() : null;
    Instant validatedAt = Instant.now();

    if (schemaIds != null && !schemaIds.isEmpty()) {
      return validateRdfAssetWithExplicitSchemas(asset, schemaIds, validatedAt);
    }
    if (Boolean.TRUE.equals(validateAll)) {
      return validateRdfAssetAgainstAll(asset, validatedAt);
    }
    throw new NotFoundException(
        "No schemas specified for validation. Provide schemaIds or set validateAgainstAllSchemas=true.");
  }

  /**
   * Validates an RDF asset against explicitly provided schema IDs.
   *
   * <p>Schema IDs are grouped by type. SHAPE schemas run via SHACL. JSON schemas run via
   * JSON Schema (only if the asset is JSON-LD). XML schemas run via XML Schema (only if
   * the asset is RDF/XML). Each type stores an independent result.</p>
   */
  private ValidationResponse validateRdfAssetWithExplicitSchemas(
      AssetMetadata asset, List<String> schemaIds, Instant validatedAt) {
    Map<SchemaType, List<String>> grouped = groupSchemasByType(schemaIds);
    List<String> shapeIds = grouped.getOrDefault(SchemaType.SHAPE, List.of());
    List<String> jsonIds = grouped.getOrDefault(SchemaType.JSON, List.of());
    List<String> xmlIds = grouped.getOrDefault(SchemaType.XML, List.of());

    // Read asset content once — avoids repeated file/S3 reads across format checks and validators
    String contentStr = asset.getContentAccessor().getContentAsString();
    ContentAccessorDirect assetContent = new ContentAccessorDirect(contentStr);

    if (!jsonIds.isEmpty() && !isJsonLdContent(contentStr)) {
      throw new ClientException(
          "JSON Schema validation requires a JSON-LD serialized RDF asset, "
              + "but asset " + asset.getId() + " is not JSON-LD. "
              + "JSON Schema is applicable to JSON-LD and application/vc+ld+json assets.");
    }
    if (!xmlIds.isEmpty() && !isRdfXmlContent(contentStr)) {
      throw new ClientException(
          "XML Schema validation requires an RDF/XML serialized asset, "
              + "but asset " + asset.getId() + " is not RDF/XML. "
              + "XML Schema is applicable to application/rdf+xml assets.");
    }

    List<String> allSchemaIds = new ArrayList<>();
    List<Long> allResultIds = new ArrayList<>();
    boolean overallConforms = true;
    ValidationReport firstReport = null;
    ValidationReport failingReport = null;

    if (!shapeIds.isEmpty()) {
      requireModuleEnabled(SchemaModuleType.SHACL);
      SchemaResolutionResult shaclSchemas = resolveShaclSchemas(shapeIds, null);
      ValidationReport report = shaclValidator.validate(List.of(asset), shaclSchemas.shapesModel());
      allResultIds.add(storeResult(
          List.of(asset.getId()), shaclSchemas.validatorIds(), ValidatorType.SHACL, report, validatedAt));
      allSchemaIds.addAll(shaclSchemas.schemaIds());
      overallConforms = report.getConforms();
      firstReport = report;
      if (!report.getConforms()) failingReport = report;
    }

    if (!jsonIds.isEmpty()) {
      if (jsonIds.size() > 1) {
        throw new ClientException("JSON Schema validation supports exactly one schema per request, but "
            + jsonIds.size() + " were provided.");
      }
      requireModuleEnabled(SchemaModuleType.JSON_SCHEMA);
      ContentAccessor jsonSchema = schemaStore.getSchema(jsonIds.get(0));
      ValidationReport report = jsonSchemaValidator.validate(assetContent, jsonSchema);
      allResultIds.add(storeResult(
          List.of(asset.getId()), jsonIds, ValidatorType.JSON_SCHEMA, report, validatedAt));
      allSchemaIds.addAll(jsonIds);
      overallConforms = overallConforms && report.getConforms();
      if (firstReport == null) firstReport = report;
      if (!report.getConforms() && failingReport == null) failingReport = report;
    }

    if (!xmlIds.isEmpty()) {
      if (xmlIds.size() > 1) {
        throw new ClientException("XML Schema validation supports exactly one schema per request, but "
            + xmlIds.size() + " were provided.");
      }
      requireModuleEnabled(SchemaModuleType.XML_SCHEMA);
      ContentAccessor xmlSchema = schemaStore.getSchema(xmlIds.get(0));
      ValidationReport report = xmlSchemaValidator.validate(assetContent, xmlSchema);
      allResultIds.add(storeResult(
          List.of(asset.getId()), xmlIds, ValidatorType.XML_SCHEMA, report, validatedAt));
      allSchemaIds.addAll(xmlIds);
      overallConforms = overallConforms && report.getConforms();
      if (firstReport == null) firstReport = report;
      if (!report.getConforms() && failingReport == null) failingReport = report;
    }

    if (allResultIds.isEmpty()) {
      throw new ClientException("None of the provided schemas are applicable to this RDF asset.");
    }

    ValidationReport responseReport = failingReport != null ? failingReport : firstReport;
    log.debug("validateRdfAssetWithExplicitSchemas.exit; assetId={}, conforms={}", asset.getId(), overallConforms);
    return buildResponse(List.of(asset.getId()), allSchemaIds, responseReport, allResultIds, validatedAt);
  }

  /**
   * Validates an RDF asset against all applicable stored schemas.
   *
   * <p>Each paradigm runs only if its module is enabled and schemas of that type are stored.
   * Disabled modules are silently skipped — this is the "all <em>applicable</em>" semantics from
   * the SRS. Throws {@link VerificationException} (422) only when every applicable paradigm is
   * either disabled or has no stored schemas.</p>
   *
   * <ul>
   *   <li>SHACL — runs if module enabled and at least one SHACL shape is stored.</li>
   *   <li>JSON Schema — additionally runs if asset is JSON-LD and JSON_SCHEMA module is enabled.</li>
   *   <li>XML Schema — additionally runs if asset is RDF/XML and XML_SCHEMA module is enabled.</li>
   * </ul>
   *
   * <p>Each paradigm stores an independent result. The primary result ID is from the first paradigm
   * that ran. Response report is the first failing one, or the first result if all passed.</p>
   */
  private ValidationResponse validateRdfAssetAgainstAll(AssetMetadata asset, Instant validatedAt) {
    // Load schema list once — avoids repeated DB calls (one per schema type below)
    Map<SchemaType, List<String>> schemasByType = schemaStore.getSchemaList();
    // Read asset content once — avoids repeated file/S3 reads across format checks and validators
    String contentStr = asset.getContentAccessor().getContentAsString();
    ContentAccessorDirect assetContent = new ContentAccessorDirect(contentStr);

    List<String> allSchemaIds = new ArrayList<>();
    List<Long> allResultIds = new ArrayList<>();
    boolean overallConforms = true;
    ValidationReport firstReport = null;
    ValidationReport failingReport = null;

    if (moduleConfig.isModuleEnabled(SchemaModuleType.SHACL)) {
      ContentAccessor composite = schemaStore.getCompositeSchema(SchemaType.SHAPE);
      if (composite != null) {
        Model shapesModel = shaclValidator.parseShapeModel(composite);
        List<String> shaclIds = schemasByType.getOrDefault(SchemaType.SHAPE, List.of());
        ValidationReport shaclReport = shaclValidator.validate(List.of(asset), shapesModel);
        allResultIds.add(storeResult(
            List.of(asset.getId()), shaclIds, ValidatorType.SHACL, shaclReport, validatedAt));
        allSchemaIds.addAll(shaclIds);
        overallConforms = shaclReport.getConforms();
        firstReport = shaclReport;
        if (!shaclReport.getConforms()) failingReport = shaclReport;
      }
    }

    if (isJsonLdContent(contentStr) && moduleConfig.isModuleEnabled(SchemaModuleType.JSON_SCHEMA)) {
      ContentAccessor jsonSchema = schemaStore.getLatestSchemaByType(SchemaType.JSON);
      if (jsonSchema != null) {
        List<String> jsonIds = schemasByType.getOrDefault(SchemaType.JSON, List.of());
        ValidationReport jsonReport = jsonSchemaValidator.validate(assetContent, jsonSchema);
        allResultIds.add(storeResult(
            List.of(asset.getId()), jsonIds, ValidatorType.JSON_SCHEMA, jsonReport, validatedAt));
        allSchemaIds.addAll(jsonIds);
        overallConforms = overallConforms && jsonReport.getConforms();
        if (firstReport == null) firstReport = jsonReport;
        if (!jsonReport.getConforms() && failingReport == null) failingReport = jsonReport;
      }
    }

    if (isRdfXmlContent(contentStr) && moduleConfig.isModuleEnabled(SchemaModuleType.XML_SCHEMA)) {
      ContentAccessor xmlSchema = schemaStore.getLatestSchemaByType(SchemaType.XML);
      if (xmlSchema != null) {
        List<String> xmlIds = schemasByType.getOrDefault(SchemaType.XML, List.of());
        ValidationReport xmlReport = xmlSchemaValidator.validate(assetContent, xmlSchema);
        allResultIds.add(storeResult(
            List.of(asset.getId()), xmlIds, ValidatorType.XML_SCHEMA, xmlReport, validatedAt));
        allSchemaIds.addAll(xmlIds);
        overallConforms = overallConforms && xmlReport.getConforms();
        if (firstReport == null) firstReport = xmlReport;
        if (!xmlReport.getConforms() && failingReport == null) failingReport = xmlReport;
      }
    }

    if (allResultIds.isEmpty()) {
      throw new VerificationException(
          "No validation module is enabled or applicable for asset " + asset.getId()
              + ". Enable at least one applicable module via admin settings (SHACL, "
              + SchemaModuleType.JSON_SCHEMA + ", or " + SchemaModuleType.XML_SCHEMA + ").");
    }

    ValidationReport responseReport = failingReport != null ? failingReport : firstReport;
    log.debug("validateRdfAssetAgainstAll.exit; assetId={}, conforms={}", asset.getId(), overallConforms);
    return buildResponse(List.of(asset.getId()), allSchemaIds, responseReport, allResultIds, validatedAt);
  }

  // --- Non-RDF asset ---

  private ValidationResponse validateNonRdfAsset(AssetMetadata asset, SingleAssetValidationRequest request) {
    String contentType = asset.getContentType();
    if (contentType == null) {
      throw new VerificationException(
          "Asset " + asset.getId() + " has no content type. Cannot determine validation schema type. "
              + "Validatable types: application/json, application/xml");
    }
    if (contentType.contains(MediaType.APPLICATION_JSON_VALUE) || contentType.contains(MEDIA_TYPE_SCHEMA_JSON)) {
      return validateWithJsonSchema(asset, request);
    }
    if (contentType.contains(MediaType.APPLICATION_XML_VALUE) || contentType.contains(MediaType.TEXT_XML_VALUE)) {
      return validateWithXmlSchema(asset, request);
    }
    throw new VerificationException(
        "Asset " + asset.getId() + " has unsupported content type for validation: " + contentType
            + ". Validatable types: application/json, application/xml");
  }

  private ValidationResponse validateWithJsonSchema(AssetMetadata asset, SingleAssetValidationRequest request) {
    requireModuleEnabled(SchemaModuleType.JSON_SCHEMA);
    List<String> schemaIds = request != null ? request.getSchemaIds() : null;
    Boolean validateAll = request != null ? request.getValidateAgainstAllSchemas() : null;
    SchemaContentResult schemas = resolveNonRdfSchema(schemaIds, validateAll, SchemaType.JSON, asset.getId());

    ContentAccessor assetContent = readFileStoreContent(asset);
    ValidationReport report = jsonSchemaValidator.validate(assetContent, schemas.schemaContent());
    Instant validatedAt = Instant.now();
    Long resultId = storeResult(
      List.of(asset.getId()), schemas.validatorIds(), ValidatorType.JSON_SCHEMA, report, validatedAt);

    return buildResponse(List.of(asset.getId()), schemas.schemaIds(), report, List.of(resultId), validatedAt);
  }

  private ValidationResponse validateWithXmlSchema(AssetMetadata asset, SingleAssetValidationRequest request) {
    requireModuleEnabled(SchemaModuleType.XML_SCHEMA);
    List<String> schemaIds = request != null ? request.getSchemaIds() : null;
    Boolean validateAll = request != null ? request.getValidateAgainstAllSchemas() : null;
    SchemaContentResult schemas = resolveNonRdfSchema(schemaIds, validateAll, SchemaType.XML, asset.getId());

    ContentAccessor assetContent = readFileStoreContent(asset);
    ValidationReport report = xmlSchemaValidator.validate(assetContent, schemas.schemaContent());
    Instant validatedAt = Instant.now();
    Long resultId = storeResult(
      List.of(asset.getId()), schemas.validatorIds(), ValidatorType.XML_SCHEMA, report, validatedAt);

    return buildResponse(List.of(asset.getId()), schemas.schemaIds(), report, List.of(resultId), validatedAt);
  }

  // --- Schema resolution ---

  /**
   * Resolves SHACL shapes into a merged Jena Model.
   *
   * <p>If explicit schemaIds are given, each is type-checked (must be SHAPE) and loaded.
   * Multiple shapes are merged via {@code Model.add()}. If validateAgainstAllSchemas=true,
   * the composite schema (union of all stored shapes) is used directly.</p>
   */
  private SchemaResolutionResult resolveShaclSchemas(List<String> schemaIds, Boolean validateAll) {
    if (schemaIds != null && !schemaIds.isEmpty()) {
      Model merged = ModelFactory.createDefaultModel();
      List<String> resolvedIds = new ArrayList<>();
      for (String id : schemaIds) {
        SchemaRecord record = schemaStore.getSchemaRecord(id);
        if (record.type() != SchemaType.SHAPE) {
          throw new ClientException(
              "Schema " + id + " has type " + record.type()
                  + " and cannot be used for SHACL validation. Only SHAPE schemas are accepted.");
        }
        ContentAccessor shape = schemaStore.getSchema(id);
        merged.add(shaclValidator.parseShapeModel(shape));
        resolvedIds.add(id);
      }
      return new SchemaResolutionResult(merged, resolvedIds, resolvedIds);
    }

    if (Boolean.TRUE.equals(validateAll)) {
      ContentAccessor composite = schemaStore.getCompositeSchema(SchemaType.SHAPE);
      if (composite == null) {
        throw new NotFoundException("No SHACL shapes found for validation");
      }
      Model shapesModel = shaclValidator.parseShapeModel(composite);
      List<String> allIds = schemaStore.getSchemaList().getOrDefault(SchemaType.SHAPE, List.of());
      return new SchemaResolutionResult(shapesModel, allIds, allIds);
    }

    throw new NotFoundException(
        "No schemas specified for validation. Provide schemaIds or set validateAgainstAllSchemas=true.");
  }

  /**
   * Resolves a single non-RDF (JSON or XML) schema for validation.
   */
  private SchemaContentResult resolveNonRdfSchema(
      List<String> schemaIds, Boolean validateAll, SchemaType expectedType, String assetId) {
    if (schemaIds != null && !schemaIds.isEmpty()) {
      if (schemaIds.size() > 1) {
        throw new ClientException(
            "JSON and XML Schema validation supports exactly one schema per request, but "
                + schemaIds.size() + " were provided.");
      }
      String id = schemaIds.get(0);
      SchemaRecord record = schemaStore.getSchemaRecord(id);
      if (record.type() != expectedType) {
        throw new ClientException(
            "Schema " + id + " has type " + record.type()
                + " but asset " + assetId + " requires a " + expectedType + " schema.");
      }
      return new SchemaContentResult(schemaStore.getSchema(id), List.of(id), List.of(id));
    }

    if (Boolean.TRUE.equals(validateAll)) {
      ContentAccessor schema = schemaStore.getLatestSchemaByType(expectedType);
      if (schema == null) {
        throw new NotFoundException("No " + expectedType + " schema found for validation");
      }
      List<String> allIds = schemaStore.getSchemaList().getOrDefault(expectedType, List.of());
      return new SchemaContentResult(schema, allIds, allIds);
    }

    throw new NotFoundException(
        "No schemas specified for validation. Provide schemaIds or set validateAgainstAllSchemas=true.");
  }

  // --- Helpers ---

  /**
   * Groups schema IDs by their SchemaType, loading each record from the schema store.
   * Throws {@link ClientException} for schema types not supported in on-demand validation (e.g., ONTOLOGY).
   */
  private Map<SchemaType, List<String>> groupSchemasByType(List<String> schemaIds) {
    Map<SchemaType, List<String>> grouped = new HashMap<>();
    for (String id : schemaIds) {
      SchemaRecord record = schemaStore.getSchemaRecord(id);
      SchemaType type = record.type();
      if (type != SchemaType.SHAPE && type != SchemaType.JSON && type != SchemaType.XML) {
        throw new ClientException("Schema " + id + " has type " + type
            + " which is not supported for on-demand validation. Supported types: SHAPE, JSON, XML.");
      }
      grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
    }
    return grouped;
  }

  private boolean isJsonLdContent(String content) {
    return content.startsWith(JSON_LD_CONTENT_PREFIX);
  }

  private boolean isRdfXmlContent(String content) {
    return content.startsWith(RDF_XML_CONTENT_PREFIX_1) || content.startsWith(RDF_XML_CONTENT_PREFIX_2);
  }

  private void requireModuleEnabled(String moduleType) {
    if (!moduleConfig.isModuleEnabled(moduleType)) {
      throw new VerificationException("Validation module " + moduleType + " is disabled");
    }
  }

  private ContentAccessor readFileStoreContent(AssetMetadata asset) {
    try {
      return fileStore.readFile(asset.getAssetHash());
    } catch (IOException e) {
      throw new ServerException(
          "Failed to read asset content from file store for asset " + asset.getId() + ": " + e.getMessage(), e);
    }
  }

  private Long storeResult(List<String> assetIds, List<String> validatorIds,
      ValidatorType validatorType, ValidationReport report, Instant validatedAt) {
    String rawReport = report.getConforms() ? null : report.getRawReport();
    return validationResultStore.store(new ValidationResultRecord(
        assetIds,
        validatorIds,
        validatorType,
        report.getConforms(),
        validatedAt,
        rawReport));
  }

  private ValidationResponse buildResponse(List<String> assetIds, List<String> schemaIds,
      ValidationReport report, List<Long> validationResultIds, Instant validatedAt) {
    ValidationResponse response = new ValidationResponse();
    response.setAssetIds(assetIds);
    response.setSchemaIds(schemaIds);
    response.setConforms(report.getConforms());
    response.setValidatedAt(validatedAt);
    response.setValidationResultIds(validationResultIds);
    if (!report.getConforms()) {
      response.setReport(report);
    }
    return response;
  }

  // --- Internal DTOs ---

  /**
   * Holds a resolved SHACL shapes model, the resolved schema IDs, and validator IDs for storage.
   */
  private record SchemaResolutionResult(Model shapesModel, List<String> schemaIds, List<String> validatorIds) {
  }

  /**
   * Holds a resolved non-RDF (JSON/XML) schema, the resolved schema IDs, and validator IDs.
   */
  private record SchemaContentResult(ContentAccessor schemaContent, List<String> schemaIds,
                                     List<String> validatorIds) {
  }
}
