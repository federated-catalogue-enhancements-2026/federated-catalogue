package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import eu.xfsc.fc.core.config.VerificationStackTestConfig;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

/**
 * Pins the OWL module toggle. The toggle gates whether
 * {@link CredentialVerificationStrategy#resolveRole} consults the runtime composite
 * ontology built from {@link SchemaType#ONTOLOGY} rows.
 *
 * <p>Bundle-embedded ontologies are pre-walked into the registry tier-1 index at startup
 * (see {@code TrustFrameworkRegistry.indexBundle}), so credentials whose types are
 * subclasses of registered roots in the bundle ontology resolve via the registry regardless
 * of the OWL toggle. The toggle therefore only affects ontologies uploaded at runtime via
 * {@code schemaStore.addSchema(..., ONTOLOGY)} — those feed the runtime composite that
 * {@code resolveRole} fetches per request.
 *
 * <p>Test strategy: spy on {@link SchemaStoreImpl} and assert whether
 * {@code getCompositeSchema(ONTOLOGY)} is invoked, which is the precise observable effect
 * of the toggle gate. Outcome-level assertions (registry-direct types keep resolving) are
 * covered by {@link RoleResolutionCharacterisationTest}; null-tolerance of the downstream
 * {@code ClaimValidator.resolveSubjectRole} is covered by {@link LoireTypeResolutionTest}.
 */
@SpringBootTest(properties = {
    "federated-catalogue.verification.signature-verifier=uni-res",
    "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
    "federated-catalogue.verification.vc-signature=false",
    "federated-catalogue.verification.vp-signature=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {CredentialVerificationStrategyOwlToggleTest.TestApplication.class,
    VerificationStackTestConfig.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class CredentialVerificationStrategyOwlToggleTest {

  private static final boolean SKIP = false;
  private static final boolean DO = true;

  private static final String PARTICIPANT_FIXTURE =
      "VerificationService/syntax/participantCredential2.jsonld";

  @SpringBootApplication
  public static class TestApplication {
    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private VerificationServiceImpl verificationService;

  @MockitoSpyBean
  private SchemaStoreImpl schemaStore;

  @MockitoBean
  private SchemaModuleConfigService schemaModuleConfigService;

  @MockitoBean
  private JwtSignatureVerifier jwtSignatureVerifier;

  @BeforeEach
  void seedRuntimeOntology() {
    // Default: every toggle is enabled. Each test overrides the OWL toggle locally.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
  }

  @AfterEach
  void clear() {
    schemaStore.clear();
  }

  @Test
  void verifyCredential_owlEnabled_consultsCompositeOntology() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), SKIP, SKIP, SKIP, SKIP);

    assertNotNull(result);
    verify(schemaStore, atLeastOnce()).getCompositeSchema(SchemaType.ONTOLOGY);
  }

  @Test
  void verifyCredential_owlDisabled_skipsCompositeOntology() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), SKIP, SKIP, SKIP, SKIP);

    assertNotNull(result);
    verify(schemaStore, never()).getCompositeSchema(SchemaType.ONTOLOGY);
  }

  @Test
  void verifyCredential_owlDisabled_verifySemanticsTrue_stillSkipsComposite() {
    // The toggle is independent of the caller's verifySemantics flag — type dispatch
    // is a configuration of the catalogue's type system, not a per-request check.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), DO, SKIP, SKIP, SKIP);

    assertNotNull(result);
    verify(schemaStore, never()).getCompositeSchema(SchemaType.ONTOLOGY);
  }
}
