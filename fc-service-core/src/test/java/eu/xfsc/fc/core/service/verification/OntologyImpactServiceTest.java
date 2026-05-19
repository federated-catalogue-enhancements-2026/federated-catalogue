package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.api.generated.model.OntologyImpactEntry;
import eu.xfsc.fc.api.generated.model.OntologyImpactList;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.RoleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.ValidationType;

/**
 * Unit tests for {@link OntologyImpactService}.
 *
 * <p>Verifies that uploaded ontologies contribute the expected subclass counts under each
 * registered trust-framework role. Uses the Gaia-X 2511 test ontology fixture, which
 * declares:
 * <ul>
 *   <li>{@code gx:LegalPerson}, {@code gx:NaturalPerson} as subclasses of {@code gx:Participant}</li>
 *   <li>{@code gx:VirtualResource}, {@code gx:PhysicalResource} as subclasses of {@code gx:Resource}</li>
 *   <li>{@code gx:DataProduct} as subclass of {@code gx:DigitalServiceOffering}
 *       (which is itself an {@code additional_roots} sibling of {@code gx:ServiceOffering})</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OntologyImpactServiceTest {

  private static final String SCHEMA_ID = "schema:gx-2511-test-ontology";
  private static final String PROFILE_ID = "gaia-x-2511";
  private static final String NAMESPACE = "https://w3id.org/gaia-x/2511#";

  private static String ontologyContent;

  @Mock
  private SchemaStore schemaStore;

  @Mock
  private TrustFrameworkRegistry trustFrameworkRegistry;

  @InjectMocks
  private OntologyImpactService service;

  @BeforeAll
  static void loadFixture() throws IOException {
    String ttlPath = "Schema-Tests/gx-2511-test-ontology.ttl";
    try (InputStream is = OntologyImpactServiceTest.class
        .getClassLoader().getResourceAsStream(ttlPath)) {
      assertNotNull(is, "Test resource not found: " + ttlPath);
      ontologyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void computeImpact_noOntologiesStored_returnsEmptyList() {
    when(schemaStore.getSchemaList()).thenReturn(Map.of());
    when(trustFrameworkRegistry.getActiveBundles()).thenReturn(buildGx2511Bundles());

    OntologyImpactList result = service.computeImpact();

    assertTrue(result.getItems().isEmpty(), "no stored ontologies → empty impact list");
    assertFalse(result.getNoActiveBundles(), "bundles are active");
  }

  @Test
  void computeImpact_gx2511OntologyAgainstGx2511Bundle_returnsExpectedSubclassCounts() {
    seedSingleOntology();
    when(trustFrameworkRegistry.getActiveBundles()).thenReturn(buildGx2511Bundles());

    OntologyImpactList result = service.computeImpact();

    assertEquals(1, result.getItems().size(), "one stored ontology → one impact entry");
    OntologyImpactEntry entry = result.getItems().get(0);
    assertEquals(SCHEMA_ID, entry.getId());
    assertFalse(entry.getParseError(), "well-formed Turtle parses cleanly");
    Map<String, Integer> contributions = entry.getContributions();

    assertEquals(Integer.valueOf(2), contributions.get("Participant"),
        "gx:LegalPerson + gx:NaturalPerson → 2 subclasses under Participant");
    assertEquals(Integer.valueOf(1), contributions.get("ServiceOffering"),
        "gx:DataProduct → 1 subclass under ServiceOffering "
            + "(via the gx:DigitalServiceOffering additional_root)");
    assertEquals(Integer.valueOf(2), contributions.get("Resource"),
        "gx:VirtualResource + gx:PhysicalResource → 2 subclasses under Resource");
  }

  @Test
  void computeImpact_unparseableOntology_marksEntryWithParseError() {
    SchemaRecord garbageRecord = new SchemaRecord(
        SCHEMA_ID, SCHEMA_ID, SchemaType.ONTOLOGY,
        Instant.parse("2026-05-14T10:00:00Z"), Instant.parse("2026-05-14T10:00:00Z"),
        "not valid turtle", Set.of());
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.ONTOLOGY, List.of(SCHEMA_ID)));
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(garbageRecord);
    when(trustFrameworkRegistry.getActiveBundles()).thenReturn(buildGx2511Bundles());

    OntologyImpactList result = service.computeImpact();

    assertEquals(1, result.getItems().size());
    OntologyImpactEntry entry = result.getItems().get(0);
    assertTrue(entry.getParseError(), "parse failure surfaces as parseError=true");
    assertNotNull(entry.getParseErrorMessage(), "parseError carries a human-readable message");
    assertTrue(entry.getContributions().isEmpty(),
        "unparseable ontology contributes nothing rather than failing the whole call");
  }

  @Test
  void computeImpact_noActiveBundles_setsNoActiveBundlesFlag() {
    seedSingleOntology();
    when(trustFrameworkRegistry.getActiveBundles()).thenReturn(List.of());

    OntologyImpactList result = service.computeImpact();

    assertTrue(result.getNoActiveBundles(), "no active bundles → flag set");
    assertEquals(1, result.getItems().size());
    assertTrue(result.getItems().get(0).getContributions().isEmpty(),
        "no registered roots → no contributions to count");
  }

  private void seedSingleOntology() {
    SchemaRecord record = new SchemaRecord(
        SCHEMA_ID, SCHEMA_ID, SchemaType.ONTOLOGY,
        Instant.parse("2026-05-14T10:00:00Z"), Instant.parse("2026-05-14T10:00:00Z"),
        ontologyContent, Set.of());
    when(schemaStore.getSchemaList()).thenReturn(Map.of(SchemaType.ONTOLOGY, List.of(SCHEMA_ID)));
    when(schemaStore.getSchemaRecord(SCHEMA_ID)).thenReturn(record);
  }

  private Collection<TrustFrameworkBundle> buildGx2511Bundles() {
    Map<String, RoleConfig> roles = Map.of(
        "Participant", new RoleConfig(List.of(), List.of()),
        "ServiceOffering", new RoleConfig(
            List.of(NAMESPACE + "DigitalServiceOffering"), List.of()),
        "Resource", new RoleConfig(List.of(), List.of())
    );
    FrameworkBundleConfig config = new FrameworkBundleConfig(
        PROFILE_ID, "gaia-x", NAMESPACE, ValidationType.SHACL, roles, Map.of());
    return List.of(new TrustFrameworkBundle(config, new ContentAccessorDirect(ontologyContent), null));
  }
}
