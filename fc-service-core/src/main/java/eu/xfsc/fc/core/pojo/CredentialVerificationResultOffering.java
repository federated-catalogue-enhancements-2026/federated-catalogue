package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results for the ServiceOffering role.
 *
 * @deprecated Will be removed in story 002-3 AC-5. Use {@link CredentialVerificationResult} directly.
 */
@Deprecated
public class CredentialVerificationResultOffering extends CredentialVerificationResult {

  /**
   * Creates a service-offering credential verification result.
   *
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuer issuer of the offering
   * @param issuedDateTime issuing date of the credential
   * @param id id of credential
   * @param graphClaims RDF triples for graph-DB insertion
   * @param validators validators signing parts of the credential
   * @param role resolved role name
   * @param frameworkProfileId bundle profile identifier
   */
  public CredentialVerificationResultOffering(Instant verificationTimestamp, String lifecycleStatus, String issuer,
                                              Instant issuedDateTime, String id, List<RdfClaim> graphClaims,
                                              List<Validator> validators, String role, String frameworkProfileId) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, id, graphClaims, validators,
        role, frameworkProfileId);
  }

  @Override
  public String toString() {
    int claimCount = getGraphClaims() == null ? 0 : getGraphClaims().size();
    int validatorCount = getValidators() == null ? 0 : getValidators().size();
    return "CredentialVerificationResultOffering [id=" + getId() + ", issuer=" + getIssuer()
        + ", validatorDids=" + getValidatorDids()
        + ", issuedDateTime=" + getIssuedDateTime()
        + ", graphClaims=" + claimCount + ", validators=" + validatorCount
        + ", verificationTimestamp=" + getVerificationTimestamp()
        + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
}
