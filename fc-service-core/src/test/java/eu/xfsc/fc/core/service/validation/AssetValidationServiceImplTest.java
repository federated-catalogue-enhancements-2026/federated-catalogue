package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.api.generated.model.SingleAssetValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.api.generated.model.AssetStatus;
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
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetValidationServiceImplTest {

  @Mock
  private AssetStore assetStore;
  @Mock
  private FileStore fileStore;
  @Mock
  private SchemaStore schemaStore;
  @Mock
  private SchemaModuleConfigService moduleConfigService;
  @Mock
  private ShaclValidator shaclValidator;
  @Mock
  private JsonSchemaValidator jsonSchemaValidator;
  @Mock
  private XmlSchemaValidator xmlSchemaValidator;
  @Mock
  private ValidationResultStore validationResultStore;

  @InjectMocks
  private AssetValidationServiceImpl service;

  @BeforeEach
  void setUp() {
    // @Value fields are not injected by @InjectMocks — set to the same default as production config
    ReflectionTestUtils.setField(service, "maxAssetsPerRequest", 20);
  }

  // --- helpers ---

  private static final String ASSET_ID = "https://example.org/asset/1";
  private static final String SCHEMA_ID = "https://example.org/schema/shape/1";
  private static final String JSON_SCHEMA_ID = "https://example.org/schema/json/1";
  private static final String XML_SCHEMA_ID = "https://example.org/schema/xml/1";
  private static final String RDFXML_HASH = "hash-rdfxml";

  private static final ValidationReport CONFORMING_REPORT = new ValidationReport()
      .conforms(true).violations(List.of());

  private static final ValidationReport NON_CONFORMING_REPORT = new ValidationReport()
      .conforms(false)
      .violations(List.of(new ValidationViolation()
          .message("constraint violation")
          .severity(ValidationViolation.SeverityEnum.VIOLATION)))
      .rawReport("constraint violation");

  private static AssetMetadata buildRdfAsset(String id) {
    return new AssetMetadata("hash-rdf", id, AssetStatus.ACTIVE,
        "did:web:issuer", List.of(), Instant.now(), Instant.now(),
        new ContentAccessorDirect("{\"@context\":{}}"));
  }

  private static AssetMetadata buildRdfXmlAsset(String id) {
    return new AssetMetadata(RDFXML_HASH, id, AssetStatus.ACTIVE,
        "did:web:issuer", List.of(), Instant.now(), Instant.now(),
        new ContentAccessorDirect("<?xml version=\"1.0\"?><rdf:RDF/>"));
  }

  private static AssetMetadata buildTurtleRdfAsset(String id) {
    return new AssetMetadata("hash-turtle", id, AssetStatus.ACTIVE,
        "did:web:issuer", List.of(), Instant.now(), Instant.now(),
        new ContentAccessorDirect("@prefix ex: <https://example.org/> ."));
  }

  private static AssetMetadata buildNonRdfAsset(String id, String contentType) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId(id);
    asset.setAssetHash("hash-nonrdf");
    asset.setContentType(contentType);
    asset.setContentAccessor(null);
    return asset;
  }

  private static SchemaRecord buildShapeRecord(String id) {
    return new SchemaRecord(id, id, SchemaType.SHAPE, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null);
  }

  private static SchemaRecord buildJsonRecord(String id) {
    return new SchemaRecord(id, id, SchemaType.JSON, "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}",
        null);
  }

  private static SchemaRecord buildXmlRecord(String id) {
    return new SchemaRecord(id, id, SchemaType.XML, "<xs:schema/>", null);
  }

  private void givenShaclModuleEnabled() {
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
  }

  private void givenJsonModuleEnabled() {
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.JSON_SCHEMA)).thenReturn(true);
  }

  private void givenXmlModuleEnabled() {
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.XML_SCHEMA)).thenReturn(true);
  }

  // === validateAsset — RDF (SHACL) path ===

  @Test
  void validateAsset_rdfAsset_explicitShapeSchema_returnsConformingResponse() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(1L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(1L, response.getValidationResultId());
    assertNull(response.getReport());
  }

  @Test
  void validateAsset_rdfAsset_nonConforming_reportIncludedInResponse() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape content");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(NON_CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(2L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertFalse(response.getConforms());
    assertNotNull(response.getReport());
    assertEquals(1, response.getReport().getViolations().size());
    assertEquals("constraint violation", response.getReport().getViolations().get(0).getMessage());
  }

  @Test
  void validateAsset_rdfAsset_validateAll_usesCompositeSchema() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidator.parseShapeModel(composite)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(3L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_rdfAsset_shaclModuleDisabled_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_rdfAsset_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();

    assertThrows(NotFoundException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_rdfAsset_schemaTypeNotShape_throwsClientException() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    // Schema exists but is ONTOLOGY type — not supported for on-demand validation
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(
        new SchemaRecord(SCHEMA_ID, SCHEMA_ID, SchemaType.ONTOLOGY, "ontology content", null));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_rdfAsset_validateAll_shaclEnabledNoShapesAndNoOtherModule_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(null);
    // JSON_SCHEMA not enabled (default false) — nothing runs

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  // === validateAsset — RDF validateAll C-2: skip disabled modules ===

  @Test
  void validateAsset_rdfAsset_validateAll_allModulesDisabled_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    // no module mocked → all return false

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_jsonLdRdfAsset_validateAll_shaclDisabled_runsJsonSchemaOnly() throws IOException {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor jsonSchemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);
    givenJsonModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(jsonSchemaContent);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.JSON, List.of(JSON_SCHEMA_ID)));
    when(jsonSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(40L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(40L, response.getValidationResultId());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());
    verify(validationResultStore, times(1)).store(any());

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    assertEquals(ValidatorType.JSON_SCHEMA, captor.getValue().validatorType());
  }

  @Test
  void validateAsset_jsonLdRdfAsset_validateAll_shaclEnabledNoShapes_runsJsonSchemaOnly() throws IOException {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor jsonSchemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(null);
    givenJsonModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(jsonSchemaContent);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.JSON, List.of(JSON_SCHEMA_ID)));
    when(jsonSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(41L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(41L, response.getValidationResultId());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    assertEquals(ValidatorType.JSON_SCHEMA, captor.getValue().validatorType());
  }

  // === validateAsset — RDF cross-paradigm (CAT-FR-CO-05 Note) ===

  @Test
  void validateAsset_jsonLdRdfAsset_withExplicitJsonSchema_storesJsonSchemaResult() throws IOException {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor jsonSchemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(jsonSchemaContent);
    when(jsonSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(20L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());
    assertEquals(20L, response.getValidationResultId());

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    assertEquals(ValidatorType.JSON_SCHEMA, captor.getValue().validatorType());
  }

  @Test
  void validateAsset_rdfXmlAsset_withExplicitXmlSchema_storesXmlSchemaResult() throws IOException {
    AssetMetadata asset = buildRdfXmlAsset(ASSET_ID);
    ContentAccessor xmlSchemaContent = new ContentAccessorDirect("<xs:schema/>");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchema(XML_SCHEMA_ID)).thenReturn(xmlSchemaContent);
    when(xmlSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(21L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(XML_SCHEMA_ID), response.getSchemaIds());
    assertEquals(21L, response.getValidationResultId());

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    assertEquals(ValidatorType.XML_SCHEMA, captor.getValue().validatorType());
  }

  @Test
  void validateAsset_jsonLdRdfAsset_withShapeAndJsonSchema_storesTwoResultsAndReturnsShaclAsPrimary()
      throws IOException {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("@prefix sh: <http://www.w3.org/ns/shacl#> .");
    ContentAccessor jsonSchemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(jsonSchemaContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(jsonSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(20L, 21L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID, JSON_SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(20L, response.getValidationResultId());
    assertEquals(List.of(SCHEMA_ID, JSON_SCHEMA_ID), response.getSchemaIds());
    verify(validationResultStore, times(2)).store(any());
  }

  @Test
  void validateAsset_jsonLdRdfAsset_withShapeAndJsonSchema_jsonFails_overallConformsIsFalse()
      throws IOException {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape");
    ContentAccessor jsonSchemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(jsonSchemaContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(jsonSchemaValidator.validate(any(), any())).thenReturn(NON_CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(20L, 21L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID, JSON_SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertFalse(response.getConforms());
    assertNotNull(response.getReport());
  }

  @Test
  void validateAsset_rdfXmlAsset_withJsonSchema_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfXmlAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_jsonLdRdfAsset_withXmlSchema_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_jsonLdRdfAsset_validateAll_runsShaclAndJsonSchema() throws IOException {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("shape composite");
    ContentAccessor jsonSchemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    givenJsonModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(
        Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID), SchemaType.JSON, List.of(JSON_SCHEMA_ID)));
    when(shaclValidator.parseShapeModel(composite)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(jsonSchemaContent);
    when(jsonSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(30L, 31L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(30L, response.getValidationResultId());
    assertTrue(response.getSchemaIds().contains(SCHEMA_ID));
    assertTrue(response.getSchemaIds().contains(JSON_SCHEMA_ID));
    verify(validationResultStore, times(2)).store(any());
  }

  @Test
  void validateAsset_rdfXmlAsset_validateAll_runsShaclAndXmlSchema() throws IOException {
    AssetMetadata asset = buildRdfXmlAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("shape composite");
    ContentAccessor xmlSchemaContent = new ContentAccessorDirect("<xs:schema/>");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    givenXmlModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(
        Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID), SchemaType.XML, List.of(XML_SCHEMA_ID)));
    when(shaclValidator.parseShapeModel(composite)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(schemaStore.getLatestSchemaByType(SchemaType.XML)).thenReturn(xmlSchemaContent);
    when(xmlSchemaValidator.validate(any(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(32L, 33L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(32L, response.getValidationResultId());
    verify(validationResultStore, times(2)).store(any());
  }

  @Test
  void validateAsset_turtleRdfAsset_validateAll_runsShaclOnly() {
    AssetMetadata asset = buildTurtleRdfAsset(ASSET_ID);
    ContentAccessor composite = new ContentAccessorDirect("shape composite");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(composite);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.SHAPE, List.of(SCHEMA_ID)));
    when(shaclValidator.parseShapeModel(composite)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(34L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(34L, response.getValidationResultId());
    verify(validationResultStore, times(1)).store(any());
  }

  // === validateAsset — JSON path (Case D) ===

  @Test
  void validateAsset_jsonAsset_explicitJsonSchema_returnsConformingResponse() throws IOException {
    AssetMetadata asset = buildNonRdfAsset(ASSET_ID, "application/json");
    ContentAccessor assetContent = new ContentAccessorDirect("{\"name\":\"test\"}");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(schemaContent);
    when(fileStore.readFile("hash-nonrdf")).thenReturn(assetContent);
    when(jsonSchemaValidator.validate(assetContent, schemaContent)).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(4L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_jsonAsset_jsonModuleDisabled_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/json"));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.JSON_SCHEMA)).thenReturn(false);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_jsonAsset_schemaTypeNotJson_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();
    // Caller provided a SHAPE schema for a JSON asset — must be rejected
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_jsonAsset_multipleExplicitSchemas_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(JSON_SCHEMA_ID, "https://example.org/schema/json/2"));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_jsonAsset_validateAll_usesLatestSchema() throws IOException {
    AssetMetadata asset = buildNonRdfAsset(ASSET_ID, "application/json");
    ContentAccessor assetContent = new ContentAccessorDirect("{\"name\":\"test\"}");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(schemaContent);
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.JSON, List.of(JSON_SCHEMA_ID)));
    when(fileStore.readFile("hash-nonrdf")).thenReturn(assetContent);
    when(jsonSchemaValidator.validate(assetContent, schemaContent)).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(5L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(JSON_SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_jsonAsset_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();

    assertThrows(NotFoundException.class,
        () -> service.validateAsset(ASSET_ID, new SingleAssetValidationRequest()));
  }

  @Test
  void validateAsset_jsonAsset_validateAll_noSchemaFound_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/json"));
    givenJsonModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.JSON)).thenReturn(null);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    assertThrows(NotFoundException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  // === validateAsset — XML path (Case E) ===

  @Test
  void validateAsset_xmlAsset_explicitXmlSchema_returnsConformingResponse() throws IOException {
    AssetMetadata asset = buildNonRdfAsset(ASSET_ID, "application/xml");
    ContentAccessor assetContent = new ContentAccessorDirect("<root/>");
    ContentAccessor schemaContent = new ContentAccessorDirect("<xs:schema/>");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchema(XML_SCHEMA_ID)).thenReturn(schemaContent);
    when(fileStore.readFile("hash-nonrdf")).thenReturn(assetContent);
    when(xmlSchemaValidator.validate(assetContent, schemaContent)).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(6L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    ValidationResponse response = service.validateAsset(ASSET_ID, request);

    assertTrue(response.getConforms());
    assertEquals(List.of(ASSET_ID), response.getAssetIds());
    assertEquals(List.of(XML_SCHEMA_ID), response.getSchemaIds());
  }

  @Test
  void validateAsset_xmlAsset_xmlModuleDisabled_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/xml"));
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.XML_SCHEMA)).thenReturn(false);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_xmlAsset_schemaTypeNotXml_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_xmlAsset_multipleExplicitSchemas_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(XML_SCHEMA_ID, "https://example.org/schema/xml/2"));

    assertThrows(ClientException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_xmlAsset_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();

    assertThrows(NotFoundException.class,
        () -> service.validateAsset(ASSET_ID, new SingleAssetValidationRequest()));
  }

  @Test
  void validateAsset_xmlAsset_validateAll_noSchemaFound_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/xml"));
    givenXmlModuleEnabled();
    when(schemaStore.getLatestSchemaByType(SchemaType.XML)).thenReturn(null);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setValidateAgainstAllSchemas(true);

    assertThrows(NotFoundException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  // === validateAsset — unsupported type (Case F) ===

  @Test
  void validateAsset_unsupportedContentType_throwsVerificationException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildNonRdfAsset(ASSET_ID, "application/pdf"));

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  @Test
  void validateAsset_nonRdfAsset_nullContentType_throwsVerificationException() {
    AssetMetadata asset = buildNonRdfAsset(ASSET_ID, null);

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAsset(ASSET_ID, request));
  }

  // === validateAssets — multi-asset SHACL ===

  @Test
  void validateAssets_multipleRdfAssets_mergedValidation_returnsConformingResponse() {
    String id1 = "https://example.org/asset/1";
    String id2 = "https://example.org/asset/2";
    AssetMetadata asset1 = buildRdfAsset(id1);
    AssetMetadata asset2 = buildRdfAsset(id2);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape ttl");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(id1)).thenReturn(asset1);
    when(assetStore.getById(id2)).thenReturn(asset2);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(7L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(id1, id2));
    request.setSchemaIds(List.of(SCHEMA_ID));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertEquals(List.of(id1, id2), response.getAssetIds());
    assertEquals(List.of(SCHEMA_ID), response.getSchemaIds());
    assertEquals(7L, response.getValidationResultId());
  }

  @Test
  void validateAssets_emptyAssetIds_throwsClientException() {
    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of());

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_nullAssetIds_throwsClientException() {
    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(null);

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_tooManyAssetIds_throwsClientException() {
    List<String> ids = java.util.stream.IntStream.rangeClosed(1, 21)
        .mapToObj(i -> "https://example.org/asset/" + i)
        .toList();

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(ids);

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_nonRdfAsset_throwsVerificationException() {
    AssetMetadata nonRdf = buildNonRdfAsset("https://example.org/asset/pdf", "application/pdf");

    when(assetStore.getById(nonRdf.getId())).thenReturn(nonRdf);
    givenShaclModuleEnabled();

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(nonRdf.getId()));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_shaclModuleDisabled_throwsVerificationException() {
    when(moduleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID));

    assertThrows(VerificationException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_multipleShapeSchemas_verifyBothShapesParsedAndReturnsConforming() {
    String id2 = "https://example.org/schema/shape/2";
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shape1 = new ContentAccessorDirect("shape1");
    ContentAccessor shape2 = new ContentAccessorDirect("shape2");
    Model model1 = ModelFactory.createDefaultModel();
    Model model2 = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchemaRecord(id2)).thenReturn(buildShapeRecord(id2));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shape1);
    when(schemaStore.getSchema(id2)).thenReturn(shape2);
    when(shaclValidator.parseShapeModel(shape1)).thenReturn(model1);
    when(shaclValidator.parseShapeModel(shape2)).thenReturn(model2);
    when(shaclValidator.validate(anyList(), any())).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(8L);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(SCHEMA_ID, id2));

    ValidationResponse response = service.validateAssets(request);

    assertTrue(response.getConforms());
    assertEquals(List.of(SCHEMA_ID, id2), response.getSchemaIds());
    // Both shapes were parsed into individual models and merged
    verify(shaclValidator).parseShapeModel(shape1);
    verify(shaclValidator).parseShapeModel(shape2);
  }

  @Test
  void validateAssets_noSchemasNoValidateAll_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    givenShaclModuleEnabled();

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_schemaTypeNotShape_throwsClientException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    assertThrows(ClientException.class, () -> service.validateAssets(request));
  }

  @Test
  void validateAssets_validateAll_noShapesFound_throwsNotFoundException() {
    when(assetStore.getById(ASSET_ID)).thenReturn(buildRdfAsset(ASSET_ID));
    givenShaclModuleEnabled();
    when(schemaStore.getCompositeSchema(SchemaType.SHAPE)).thenReturn(null);

    ValidationRequest request = new ValidationRequest();
    request.setAssetIds(List.of(ASSET_ID));
    request.setValidateAgainstAllSchemas(true);

    assertThrows(NotFoundException.class, () -> service.validateAssets(request));
  }

  // === storeResult — result content ===

  @Test
  void validateAsset_storesResultWithCorrectValidatorIds() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(9L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    service.validateAsset(ASSET_ID, request);

    ArgumentCaptor<ValidationResultRecord> captor =
        ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertEquals(List.of(ASSET_ID), stored.assetIds());
    assertEquals(List.of(SCHEMA_ID), stored.validatorIds());
    assertEquals(ValidatorType.SHACL, stored.validatorType());
    assertTrue(stored.conforms());
    assertNull(stored.report());
  }

  @Test
  void validateAsset_jsonAsset_storesResultWithJsonSchemaValidatorType() throws IOException {
    AssetMetadata asset = buildNonRdfAsset(ASSET_ID, "application/json");
    ContentAccessor assetContent = new ContentAccessorDirect("{\"name\":\"test\"}");
    ContentAccessor schemaContent = new ContentAccessorDirect("{\"$schema\":\"...\"}");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenJsonModuleEnabled();
    when(schemaStore.getSchemaRecord(JSON_SCHEMA_ID)).thenReturn(buildJsonRecord(JSON_SCHEMA_ID));
    when(schemaStore.getSchema(JSON_SCHEMA_ID)).thenReturn(schemaContent);
    when(fileStore.readFile("hash-nonrdf")).thenReturn(assetContent);
    when(jsonSchemaValidator.validate(assetContent, schemaContent)).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(10L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(JSON_SCHEMA_ID));

    service.validateAsset(ASSET_ID, request);

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertEquals(List.of(ASSET_ID), stored.assetIds());
    assertEquals(List.of(JSON_SCHEMA_ID), stored.validatorIds());
    assertEquals(ValidatorType.JSON_SCHEMA, stored.validatorType());
    assertTrue(stored.conforms());
  }

  @Test
  void validateAsset_xmlAsset_storesResultWithXmlSchemaValidatorType() throws IOException {
    AssetMetadata asset = buildNonRdfAsset(ASSET_ID, "application/xml");
    ContentAccessor assetContent = new ContentAccessorDirect("<root/>");
    ContentAccessor schemaContent = new ContentAccessorDirect("<xs:schema/>");

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenXmlModuleEnabled();
    when(schemaStore.getSchemaRecord(XML_SCHEMA_ID)).thenReturn(buildXmlRecord(XML_SCHEMA_ID));
    when(schemaStore.getSchema(XML_SCHEMA_ID)).thenReturn(schemaContent);
    when(fileStore.readFile("hash-nonrdf")).thenReturn(assetContent);
    when(xmlSchemaValidator.validate(assetContent, schemaContent)).thenReturn(CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(11L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(XML_SCHEMA_ID));

    service.validateAsset(ASSET_ID, request);

    ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertEquals(List.of(ASSET_ID), stored.assetIds());
    assertEquals(List.of(XML_SCHEMA_ID), stored.validatorIds());
    assertEquals(ValidatorType.XML_SCHEMA, stored.validatorType());
    assertTrue(stored.conforms());
  }

  @Test
  void validateAsset_nonConforming_storesRawReport() {
    AssetMetadata asset = buildRdfAsset(ASSET_ID);
    ContentAccessor shapeContent = new ContentAccessorDirect("shape");
    Model shapesModel = ModelFactory.createDefaultModel();

    when(assetStore.getById(ASSET_ID)).thenReturn(asset);
    givenShaclModuleEnabled();
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(buildShapeRecord(SCHEMA_ID));
    when(schemaStore.getSchema(SCHEMA_ID)).thenReturn(shapeContent);
    when(shaclValidator.parseShapeModel(shapeContent)).thenReturn(shapesModel);
    when(shaclValidator.validate(anyList(), any(Model.class))).thenReturn(NON_CONFORMING_REPORT);
    when(validationResultStore.store(any())).thenReturn(10L);

    SingleAssetValidationRequest request = new SingleAssetValidationRequest();
    request.setSchemaIds(List.of(SCHEMA_ID));

    service.validateAsset(ASSET_ID, request);

    ArgumentCaptor<ValidationResultRecord> captor =
        ArgumentCaptor.forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());

    ValidationResultRecord stored = captor.getValue();
    assertFalse(stored.conforms());
    assertEquals("constraint violation", stored.report());
  }
}
