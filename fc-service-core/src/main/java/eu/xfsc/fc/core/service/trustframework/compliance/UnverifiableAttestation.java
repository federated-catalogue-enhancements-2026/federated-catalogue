package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Outcome indicating that the attestation presented by the asset could not be verified.
 *
 * @param rawAttestation    the raw attestation payload that was inspected
 * @param verificationError human-readable description of why verification failed
 */
public record UnverifiableAttestation(
    String rawAttestation,
    String verificationError
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return false;
  }

  public FailureCategory failureCategory() {
    return FailureCategory.UNVERIFIABLE_ATTESTATION;
  }
}
