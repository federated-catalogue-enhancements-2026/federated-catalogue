package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results for the Participant role.
 *
 * @deprecated Will be removed in story 002-3 AC-5. Use {@link CredentialVerificationResult} directly.
 */
@Deprecated
@lombok.Getter
@lombok.Setter
public class CredentialVerificationResultParticipant extends CredentialVerificationResult {

  /**
   * The Name of the Participant.
   */
  @JsonIgnore
  private String participantName;

  /**
   * The public key of the participant.
   */
  @JsonIgnore
  private String participantPublicKey;

  /**
   * Creates a participant credential verification result.
   *
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuer issuer of the credential
   * @param issuedDateTime issuing date of the credential
   * @param id id of credential
   * @param graphClaims RDF triples for graph-DB insertion
   * @param validators validators signing parts of the credential
   * @param role resolved role name
   * @param frameworkProfileId bundle profile identifier
   * @param participantName display name of the participant
   * @param participantPublicKey DID URI of the first validator
   */
  public CredentialVerificationResultParticipant(Instant verificationTimestamp, String lifecycleStatus,
                                                 String issuer, Instant issuedDateTime,
                                                 String id, List<RdfClaim> graphClaims, List<Validator> validators,
                                                 String role, String frameworkProfileId,
                                                 String participantName, String participantPublicKey) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, id, graphClaims, validators,
        role, frameworkProfileId);
    this.participantName = participantName;
    this.participantPublicKey = participantPublicKey;
    setName(participantName);
    setPublicKey(participantPublicKey);
  }

  @Override
  public String toString() {
    int claimCount = getGraphClaims() == null ? 0 : getGraphClaims().size();
    int validatorCount = getValidators() == null ? 0 : getValidators().size();
    return "CredentialVerificationResultParticipant [id=" + getId() + ", participantName=" + participantName
        + ", issuer=" + getIssuer() + ", validatorDids=" + getValidatorDids()
        + ", issuedDateTime=" + getIssuedDateTime()
        + ", participantPublicKey=" + participantPublicKey
        + ", graphClaims=" + claimCount + ", validators=" + validatorCount
        + ", verificationTimestamp=" + getVerificationTimestamp()
        + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
}
