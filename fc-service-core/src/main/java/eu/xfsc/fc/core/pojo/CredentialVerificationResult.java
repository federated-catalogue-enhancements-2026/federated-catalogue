package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Getter
public class CredentialVerificationResult extends eu.xfsc.fc.api.generated.model.VerificationResult implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * credentialSubject (id) of this credential.
   */
  @Setter
  @JsonIgnore
  private String id;

  /** Graph-DB triples extracted from the credential. Not exposed in the JSON response. */
  @Setter
  @JsonIgnore
  private List<RdfClaim> graphClaims;

  /**
   * Returns graph claims; never null — empty list when not populated (e.g. after JSON deserialization).
   */
  public List<RdfClaim> getGraphClaims() {
    return graphClaims != null ? graphClaims : List.of();
  }

  /** Validators that signed parts of the credential. */
  @JsonIgnore
  private List<Validator> validators;

  /**
   * Resolved trust-framework role name (e.g. {@code "Participant"}, {@code "ServiceOffering"}).
   */
  @Setter
  private String role;

  /**
   * Active bundle profile identifier that resolved the role (e.g. {@code "gaia-x-2511"}).
   */
  @Setter
  private String frameworkProfileId;

  /**
   * Display name of the credential holder. Populated for the Participant role only.
   */
  @Setter
  private String name;

  /**
   * DID URI of the first validator. Populated for the Participant role only.
   */
  @Setter
  private String publicKey;

  /**
   * Raw claims from the credential, keyed by claim property URI.
   */
  @Setter
  private Map<String, Object> claims = new HashMap<>();

  /**
   * Creates a generic credential verification result.
   *
   * @param verificationTimestamp time the verification was performed
   * @param lifecycleStatus       GAIA-X lifecycle status of the credential
   * @param issuer                DID URI of the credential issuer
   * @param issuedDateTime        issuance date of the credential
   * @param id                    credentialSubject identifier
   * @param graphClaims           RDF triples for graph-DB insertion
   * @param validators            validators that signed parts of the credential
   * @param role                  resolved trust-framework role name
   * @param frameworkProfileId    bundle profile identifier that resolved the role
   */
  public CredentialVerificationResult(Instant verificationTimestamp, String lifecycleStatus, String issuer,
                                      Instant issuedDateTime, String id, List<RdfClaim> graphClaims,
                                      List<Validator> validators, String role, String frameworkProfileId) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, new java.util.ArrayList<>(),
        new java.util.ArrayList<>());
    this.id = id;
    this.graphClaims = graphClaims;
    this.role = role;
    this.frameworkProfileId = frameworkProfileId;
    setValidators(validators);
    if (graphClaims != null && !graphClaims.isEmpty()) {
      this.claims = buildClaimsMap(graphClaims);
    }
  }

  private static Map<String, Object> buildClaimsMap(List<RdfClaim> graphClaims) {
    Map<String, List<String>> grouped = new LinkedHashMap<>();
    for (RdfClaim claim : graphClaims) {
      String key = claim.getPredicateValue();
      String val = claim.getObjectValue();
      if (key != null) {
        grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
      }
    }
    Map<String, Object> result = new LinkedHashMap<>(grouped.size());
    for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
      List<String> values = entry.getValue();
      result.put(entry.getKey(), values.size() == 1 ? values.getFirst() : values);
    }
    return result;
  }

  /**
   * Updates the validator list and mirrors DID URIs into the parent VerificationResult.
   *
   * @param validators validators to set
   */
  public void setValidators(List<Validator> validators) {
    if (validators == null) {
      super.setValidatorDids(new java.util.ArrayList<>());
    } else {
      super.setValidatorDids(validators.stream().map(Validator::getDidURI).collect(Collectors.toList()));
    }
    this.validators = validators;
  }

  @Override
  public String toString() {
    int graphClaimCount = graphClaims == null ? 0 : graphClaims.size();
    int validatorCount = validators == null ? 0 : validators.size();
    return "CredentialVerificationResult [id=" + id + ", issuer=" + getIssuer()
        + ", role=" + role + ", frameworkProfileId=" + frameworkProfileId
        + ", validatorDids=" + getValidatorDids()
        + ", issuedDateTime=" + getIssuedDateTime()
        + ", graphClaims=" + graphClaimCount + ", validators=" + validatorCount
        + ", verificationTimestamp=" + getVerificationTimestamp()
        + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
}
