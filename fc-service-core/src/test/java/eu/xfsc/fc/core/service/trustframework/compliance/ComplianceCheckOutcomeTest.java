package eu.xfsc.fc.core.service.trustframework.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the {@link ComplianceCheckOutcome} sealed hierarchy.
 * No Spring context required.
 */
class ComplianceCheckOutcomeTest {

  @Test
  void issuedAttestation_isCompliant() {
    // Arrange
    var outcome = new IssuedAttestation(
        "did:web:issuer.example",
        Instant.parse("2025-12-31T00:00:00Z"),
        null
    );

    // Act + Assert
    assertTrue(outcome.compliant());
    assertEquals("did:web:issuer.example", outcome.attestationSignerDid());
    assertNull(outcome.attestationCredential());
  }

  @Test
  void unverifiableAttestation_isNotCompliant_withCategory() {
    // Arrange
    var outcome = new UnverifiableAttestation(
        FailureCategory.UNVERIFIABLE_ATTESTATION,
        "{\"raw\": \"jwt-here\"}",
        "Signature verification failed"
    );

    // Act + Assert
    assertFalse(outcome.compliant());
    assertEquals(FailureCategory.UNVERIFIABLE_ATTESTATION, outcome.failureCategory());
    assertEquals("{\"raw\": \"jwt-here\"}", outcome.rawAttestation());
    assertEquals("Signature verification failed", outcome.verificationError());
  }

  @Test
  void sealedInterface_exhaustivePatternMatch() {
    // Compile-time exhaustiveness check via switch expression — will not compile
    // if a new permitted subtype is added without updating the switch.
    ComplianceCheckOutcome outcome = new IssuedAttestation(
        "did:web:test",
        Instant.now(),
        null
    );

    // Act
    String label = switch (outcome) {
      case IssuedAttestation ia -> "issued";
      case UnverifiableAttestation ua -> "unverifiable";
    };

    // Assert
    assertEquals("issued", label);
  }
}
