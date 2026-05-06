package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TrustFrameworkRegistryImplTest {

  // Minimal ontology: Participant as a root class, no subclasses declared.
  private static final String MINIMAL_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      gx:Participant a owl:Class .
      """;

  // Ontology with declared subclass: LegalPerson rdfs:subClassOf Participant.
  private static final String SUBCLASS_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      gx:Participant a owl:Class .
      gx:LegalPerson a owl:Class ; rdfs:subClassOf gx:Participant .
      """;

  private static TrustFrameworkBundle shaclBundle(String profileId, String namespace,
                                                  Map<String, RoleConfig> roles, String ontologyTtl) {
    var config = new FrameworkBundleConfig(profileId, "gaia-x", namespace, ValidationType.SHACL, roles, Map.of());
    var ontology = new eu.xfsc.fc.core.pojo.ContentAccessorDirect(ontologyTtl);
    return new TrustFrameworkBundle(config, ontology, null);
  }

  @Test
  void resolveRole_unknownType_returnsUnknown() {
    var registry = new TrustFrameworkRegistryImpl(List.of());

    assertThat(registry.resolveRole("https://example.org/Unknown")).isEqualTo(ResolvedRole.UNKNOWN);
  }

  @Test
  void resolveRole_rootUri_returnsResolvedRole() {
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);

    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.resolveRole("https://w3id.org/gaia-x/Participant"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "Participant"));
  }

  // Ontology where DigitalServiceOffering is NOT a subclass of ServiceOffering (gx-2511 reality).
  private static final String DSO_ONTOLOGY = """
      @prefix gx: <https://w3id.org/gaia-x/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      gx:ServiceOffering a owl:Class .
      gx:DigitalServiceOffering a owl:Class .
      """;

  @Test
  void resolveRole_subclassOfRoot_returnsResolvedRole() {
    var roles = Map.of("Participant", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, SUBCLASS_ONTOLOGY);

    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    // gx:LegalPerson rdfs:subClassOf gx:Participant — must resolve to Participant
    assertThat(registry.resolveRole("https://w3id.org/gaia-x/LegalPerson"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "Participant"));
  }

  private static TrustFrameworkBundle jsonSchemaBundle(String profileId) {
    var config = new FrameworkBundleConfig(profileId, "test", "https://test/", ValidationType.JSON_SCHEMA,
        Map.of(), Map.of());
    return new TrustFrameworkBundle(config, null, null);
  }

  // additionalRoots: DigitalServiceOffering is NOT a subclass of ServiceOffering in OWL
  // but is covered via additionalRoots in RoleConfig (gx-2511 workaround)
  @Test
  void resolveRole_additionalRoot_returnsResolvedRole() {
    var roles = Map.of("ServiceOffering", new RoleConfig(
        List.of("https://w3id.org/gaia-x/DigitalServiceOffering"), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, DSO_ONTOLOGY);

    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.resolveRole("https://w3id.org/gaia-x/DigitalServiceOffering"))
        .isEqualTo(new ResolvedRole("gaia-x-2511", "ServiceOffering"));
  }

  // bundle index methods
  @Test
  void getBundles_returnsAllLoadedBundles() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.getBundles()).containsExactly(bundle);
  }

  @Test
  void getBundle_knownProfileId_returnsBundle() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.getBundle("gaia-x-2511")).contains(bundle);
  }

  @Test
  void getBundle_unknownProfileId_returnsEmpty() {
    var registry = new TrustFrameworkRegistryImpl(List.of());

    assertThat(registry.getBundle("no-such-framework")).isEmpty();
  }

  @Test
  void isFrameworkEnabled_knownBundle_returnsTrue() {
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", Map.of(), MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.isFrameworkEnabled("gaia-x-2511")).isTrue();
  }

  @Test
  void isFrameworkEnabled_unknownBundle_returnsFalse() {
    var registry = new TrustFrameworkRegistryImpl(List.of());

    assertThat(registry.isFrameworkEnabled("no-such-framework")).isFalse();
  }

  @Test
  void getEffectiveRoles_knownBundle_returnsRoleNames() {
    var roles = Map.of(
        "Participant", new RoleConfig(List.of(), List.of()),
        "ServiceOffering", new RoleConfig(List.of(), List.of()));
    var bundle = shaclBundle("gaia-x-2511", "https://w3id.org/gaia-x/", roles, MINIMAL_ONTOLOGY);
    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.getEffectiveRoles("gaia-x-2511"))
        .containsExactlyInAnyOrder("Participant", "ServiceOffering");
  }

  @Test
  void getEffectiveRoles_unknownBundle_returnsEmpty() {
    var registry = new TrustFrameworkRegistryImpl(List.of());

    assertThat(registry.getEffectiveRoles("no-such-framework")).isEmpty();
  }

  // json-schema bundle: accepted but excluded from type index
  @Test
  void resolveRole_jsonSchemaBundle_returnsUnknown() {
    var bundle = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    assertThat(registry.resolveRole("https://example.org/SomeType")).isEqualTo(ResolvedRole.UNKNOWN);
  }

  @Test
  void isFrameworkEnabled_jsonSchemaBundle_returnsTrue() {
    var bundle = jsonSchemaBundle("untp-v1");
    var registry = new TrustFrameworkRegistryImpl(List.of(bundle));

    // Bundle is loaded (enabled) even though its types are not indexed
    assertThat(registry.isFrameworkEnabled("untp-v1")).isTrue();
  }
}
