package eu.xfsc.fc.core.service.trustframework.compliance;

import java.time.Instant;

/**
 * Outcome indicating that the trust framework issued a verifiable attestation credential
 * for the asset under check.
 *
 * @param attestationSignerDid  DID of the entity that signed the attestation
 * @param credentialValidUntil  expiry timestamp of the issued credential
 * @param attestationCredential raw attestation credential string, or {@code null} if not retained
 */
public record IssuedAttestation(
    String attestationSignerDid,
    Instant credentialValidUntil,
    String attestationCredential
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return true;
  }
}
