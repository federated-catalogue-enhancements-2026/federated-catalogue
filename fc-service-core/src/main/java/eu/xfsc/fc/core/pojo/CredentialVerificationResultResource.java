package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results for the Resource role.
 *
 * @deprecated Use {@link CredentialVerificationResult} directly.
 */
@Deprecated
public class CredentialVerificationResultResource extends CredentialVerificationResult {

  /**
   * Creates a resource credential verification result.
   *
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuer issuer of the resource
   * @param issuedDateTime issuing date of the credential
   * @param id id of credential
   * @param graphClaims RDF triples for graph-DB insertion
   * @param validators validators signing parts of the credential
   * @param role resolved role name
   * @param frameworkProfileId bundle profile identifier
   */
  public CredentialVerificationResultResource(Instant verificationTimestamp, String lifecycleStatus, String issuer,
                                              Instant issuedDateTime, String id, List<RdfClaim> graphClaims,
                                              List<Validator> validators, String role, String frameworkProfileId) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, id, graphClaims, validators,
        role, frameworkProfileId);
  }

  @Override
  public String toString() {
    int claimCount = getGraphClaims() == null ? 0 : getGraphClaims().size();
    int validatorCount = getValidators() == null ? 0 : getValidators().size();
    return "CredentialVerificationResultResource [id=" + getId() + ", issuer=" + getIssuer()
        + ", validatorDids=" + getValidatorDids()
        + ", issuedDateTime=" + getIssuedDateTime()
        + ", graphClaims=" + claimCount + ", validators=" + validatorCount
        + ", verificationTimestamp=" + getVerificationTimestamp()
        + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
}
