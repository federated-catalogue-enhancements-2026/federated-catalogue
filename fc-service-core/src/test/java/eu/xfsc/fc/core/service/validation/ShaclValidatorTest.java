package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apicatalog.jsonld.loader.DocumentLoader;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ShaclValidator.class)
@TestPropertySource(properties = {"federated-catalogue.validation.shacl.timeout-seconds=10"})
class ShaclValidatorTest {

  private static final String SHAPE_PERSON_NAME_REQUIRED = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .
      ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [ sh:path ex:name ; sh:minCount 1 ] .
      """;

  private static final String TURTLE_PERSON_WITH_NAME =
      "@prefix ex: <http://example.org/> . ex:Alice a ex:Person ; ex:name \"Alice\" .";

  private static final String TURTLE_PERSON_WITHOUT_NAME =
      "@prefix ex: <http://example.org/> . ex:Bob a ex:Person .";

  @MockitoBean(name = "contextCacheFileStore")
  private FileStore fileStore;
  @MockitoBean
  private DocumentLoader documentLoader;
  @MockitoBean
  private LoireJwtParser loireJwtParser;

  @Autowired
  private ShaclValidator shaclValidator;

  @Test
  void validate_conformingTurtleAsset_returnsConforming() {
    Model shapesModel = shaclValidator.parseShapeModel(new ContentAccessorDirect(SHAPE_PERSON_NAME_REQUIRED));
    AssetMetadata asset = buildTurtleAsset("http://example.org/alice", TURTLE_PERSON_WITH_NAME);

    ValidationReport report = shaclValidator.validate(List.of(asset), shapesModel);

    assertTrue(report.getConforms(), "Conforming Turtle asset should pass SHACL validation");
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty(), "Conforming result should have no violations");
  }

  @Test
  void validate_nonConformingTurtleAsset_returnsViolation() {
    Model shapesModel = shaclValidator.parseShapeModel(new ContentAccessorDirect(SHAPE_PERSON_NAME_REQUIRED));
    AssetMetadata asset = buildTurtleAsset("http://example.org/bob", TURTLE_PERSON_WITHOUT_NAME);

    ValidationReport report = shaclValidator.validate(List.of(asset), shapesModel);

    assertFalse(report.getConforms(), "Non-conforming Turtle asset should fail SHACL validation");
    assertNotNull(report.getViolations());
    assertFalse(report.getViolations().isEmpty(), "Non-conforming result should report violations");
    assertNotNull(report.getRawReport(), "Raw SHACL report should be populated for non-conforming result");
  }

  @Test
  void validate_multipleAssets_mergesGraphsBeforeValidation() {
    // Shape requires both ex:name and ex:age on every ex:Person.
    // Each asset carries only one property; together they're conforming.
    String shapeNameAndAge = """
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        @prefix ex: <http://example.org/> .
        ex:PersonShape a sh:NodeShape ;
            sh:targetClass ex:Person ;
            sh:property [ sh:path ex:name ; sh:minCount 1 ] ;
            sh:property [ sh:path ex:age  ; sh:minCount 1 ] .
        """;
    String assetAContent = "@prefix ex: <http://example.org/> . ex:Person1 a ex:Person ; ex:name \"Eve\" .";
    String assetBContent = "@prefix ex: <http://example.org/> . ex:Person1 ex:age 30 .";

    Model shapesModel = shaclValidator.parseShapeModel(new ContentAccessorDirect(shapeNameAndAge));
    List<AssetMetadata> assets = List.of(
        buildTurtleAsset("http://example.org/a", assetAContent),
        buildTurtleAsset("http://example.org/b", assetBContent));

    ValidationReport report = shaclValidator.validate(assets, shapesModel);

    assertTrue(report.getConforms(), "Merged graph from two assets should satisfy the shape");
  }

  @Test
  void parseShapeModel_validTurtleShape_returnsNonEmptyModel() {
    Model model = shaclValidator.parseShapeModel(new ContentAccessorDirect(SHAPE_PERSON_NAME_REQUIRED));

    assertNotNull(model);
    assertFalse(model.isEmpty(), "Parsed shape model should contain triples");
  }

  @Test
  void parseShapeModel_malformedTurtle_throwsClientException() {
    assertThrows(ClientException.class,
        () -> shaclValidator.parseShapeModel(new ContentAccessorDirect("not valid turtle <<<")));
  }

  @Test
  void validate_assetWithNullContentAccessor_throwsClientException() {
    Model shapesModel = shaclValidator.parseShapeModel(new ContentAccessorDirect(SHAPE_PERSON_NAME_REQUIRED));
    AssetMetadata asset = new AssetMetadata();
    asset.setId("http://example.org/non-rdf-asset");
    asset.setContentAccessor(null);

    assertThrows(ClientException.class,
        () -> shaclValidator.validate(List.of(asset), shapesModel));
  }

  // --- helpers ---

  private static AssetMetadata buildTurtleAsset(String id, String turtleContent) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId(id);
    asset.setContentType("text/turtle");
    asset.setContentAccessor(new ContentAccessorDirect(turtleContent));
    return asset;
  }
}
