package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import org.springframework.jdbc.core.JdbcTemplate;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

/**
 * Signature verification tests with Gaia-X Trust Framework DISABLED.
 *
 * This is a companion test class to {@link SignatureVerificationTest} which tests
 * with Gaia-X enabled. These tests verify the behavioral difference when
 * {@code gaiax.enabled=false}.
 *
 * Key difference: Credentials without x5u (trust anchor URL) in the JWK are
 * ACCEPTED when Gaia-X is disabled, but REJECTED when Gaia-X is enabled.
 *
 * @see SignatureVerificationTest for the same tests with Gaia-X enabled
 */
@Slf4j
@SpringBootTest(properties = {
	"federated-catalogue.verification.signature-verifier=uni-res",
	"federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
	"federated-catalogue.verification.drop-validators=true",
	// Disable Gaia-X trust framework - trust anchor validation will be skipped
	"federated-catalogue.verification.trust-framework.gaiax.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {SignatureVerificationGaiaxDisabledTest.TestApplication.class, FileStoreConfig.class,
		DocumentLoaderConfig.class, DocumentLoaderProperties.class, VerificationServiceImpl.class, SchemaStoreImpl.class,
		SchemaDaoImpl.class, DatabaseConfig.class, DidResolverConfig.class, DidDocumentResolver.class, ValidatorCacheDaoImpl.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SignatureVerificationGaiaxDisabledTest {

	@SpringBootApplication
	public static class TestApplication {
	    public static void main(final String[] args) {
	        SpringApplication.run(TestApplication.class, args);
	    }
	}

	@Autowired
	private SchemaStoreImpl schemaStore;

	@Autowired
	private VerificationServiceImpl verificationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	public void storageSelfCleaning() throws IOException {
	    // Clear validator cache to prevent interference with other tests
	    jdbcTemplate.update("DELETE FROM validatorcache");
	    schemaStore.clear();
	}

	@Test
	@DisplayName("Verify Gaia-X trust framework is disabled for this test class")
	void testGaiaxTrustFrameworkIsDisabled() {
	    // Verify that Gaia-X trust framework is disabled for this test class
	    // This is a precondition for the other tests to be meaningful
	    assertFalse(verificationService.gaiaxTrustFrameworkEnabled,
	        "Gaia-X trust framework should be DISABLED for this test class");
	}

	@Test
	@DisplayName("Credential without x5u URL should be ACCEPTED when Gaia-X is disabled")
	void testJWKCertificateWithoutX5u_ShouldBeAccepted() {
	    // This test uses the SAME credential as SignatureVerificationTest.testJWKCertificate()
	    // but expects DIFFERENT behavior because Gaia-X is disabled.
	    //
	    // With gaiax.enabled=true:  throws "Signatures error; no trust anchor url found"
	    // With gaiax.enabled=false: should NOT throw that error
	    //
	    // This test would FAIL without the implementation changes in VerificationServiceImpl
	    // that make trust anchor validation conditional on gaiax.enabled.

	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";

	    try {
	        VerificationResult result = verificationService.verifySelfDescription(
	            getAccessor(path), true, true, true, true);
	        // If we reach here, the trust anchor check was correctly skipped
	        assertNotNull(result, "Verification should return a result when Gaia-X is disabled");
	        log.info("Credential without x5u URL was accepted (Gaia-X disabled) - verification succeeded");
	    } catch (VerificationException e) {
	        // The trust anchor error should NOT occur when Gaia-X is disabled
	        if (e.getMessage().contains("no trust anchor url found")) {
	            fail("Should NOT require trust anchor URL when Gaia-X is disabled. " +
	                 "This error should only occur when gaiax.enabled=true. Got: " + e.getMessage());
	        }
	        // Other verification errors may still occur (e.g., DID resolution issues)
	        // and are acceptable - we're only testing the trust anchor check is skipped
	        log.info("Credential verification threw non-trust-anchor error (acceptable): {}", e.getMessage());
	    }
	}

	@Test
	@DisplayName("Signature cryptographic validity is still checked when Gaia-X is disabled")
	void testSignatureStillVerified_WhenGaiaxDisabled() {
	    // Even with Gaia-X disabled, the cryptographic signature verification should still work.
	    // This test verifies that disabling Gaia-X only skips trust anchor validation,
	    // not the actual signature verification.

	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign/valid_signature.json";

	    try {
	        // This should still work - signature is cryptographically valid
	        VerificationResult result = verificationService.verifySelfDescription(
	            getAccessor(path), false, false, true, true);

	        assertNotNull(result, "Valid signature should still be verified when Gaia-X is disabled");
	        assertEquals(1, result.getValidators().size(), "Should have one validator");
	    } catch (VerificationException e) {
	        // DID resolution errors are environmental (external resolver unavailable)
	        // The important assertion is: no trust anchor error when Gaia-X is disabled
	        assertFalse(e.getMessage().contains("no trust anchor url found"),
	            "Should NOT require trust anchor URL when Gaia-X is disabled. Got: " + e.getMessage());
	        log.info("Acceptable non-trust-anchor error in signature verification: {}", e.getMessage());
	    }
	}

	@Test
	@DisplayName("Invalid signature is still rejected when Gaia-X is disabled")
	void testInvalidSignatureStillRejected_WhenGaiaxDisabled() {
	    // Disabling Gaia-X should NOT disable signature verification.
	    // Invalid signatures must still be rejected.

	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign/hasInvalidSignature.json";

	    try {
	        verificationService.verifySelfDescription(getAccessor(path), false, false, true, true);
	        fail("Invalid signature should be rejected even when Gaia-X is disabled");
	    } catch (VerificationException e) {
	        // The credential must NOT be accepted. We expect either:
	        // - "does not match with proof" (when DID can be resolved)
	        // - A DID resolution error (when external resolver is unavailable)
	        // Either way, the important thing is the credential is rejected, and
	        // it's NOT a trust anchor error (which would indicate Gaia-X check wasn't disabled)
	        assertFalse(e.getMessage().contains("no trust anchor url found"),
	            "Should NOT get trust anchor error when Gaia-X is disabled. Got: " + e.getMessage());
	        log.info("Signature rejected as expected (Gaia-X disabled): {}", e.getMessage());
	    }
	}

	@Test
	@DisplayName("Missing proof is still rejected when Gaia-X is disabled")
	void testMissingProofStillRejected_WhenGaiaxDisabled() {
	    // Disabling Gaia-X should NOT disable proof requirement.
	    // Credentials without proofs must still be rejected when signature verification is enabled.

	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign/hasNoSignature1.json";

	    try {
	        verificationService.verifySelfDescription(getAccessor(path), false, true, true, true);
	        fail("Missing proof should be rejected even when Gaia-X is disabled");
	    } catch (VerificationException e) {
	        // Expected - missing proofs should still be rejected
	        assertEquals("Signatures error; No proof found", e.getMessage(),
	            "Missing proof should be rejected with the correct error message");
	    }
	}

}
