package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.pojo.CredentialClaim;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builds PROV-O {@link CredentialClaim} triples for a provenance credential.
 *
 * <p>Maps each {@link ProvenanceType} to its corresponding W3C PROV-O predicate URI, producing a
 * single triple in N-Triples form: {@code <assetId> <prov:predicate> <issuer> .}</p>
 *
 * <p>The triple subject is the versioned asset identifier ({@code assetId:vN}), the predicate is
 * the PROV-O URI, and the object is the issuer DID.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProvOTripleBuilder {

  private static final String PROV_NS = "http://www.w3.org/ns/prov#";

  private static final String PROV_WAS_GENERATED_BY = "<" + PROV_NS + "wasGeneratedBy>";
  private static final String PROV_WAS_DERIVED_FROM = "<" + PROV_NS + "wasDerivedFrom>";
  private static final String PROV_WAS_ATTRIBUTED_TO = "<" + PROV_NS + "wasAttributedTo>";
  private static final String PROV_WAS_REVISION_OF = "<" + PROV_NS + "wasRevisionOf>";
  private static final String PROV_WAS_ASSOCIATED_WITH = "<" + PROV_NS + "wasAssociatedWith>";

  /**
   * Builds a list containing the single PROV-O triple for the given provenance credential.
   *
   * @param assetId        versioned asset identifier used as the triple subject (e.g. {@code did:example:abc:v1})
   * @param provenanceType the provenance relation type to map to a PROV-O predicate
   * @param issuer         the VC issuer DID used as the triple object
   * @return a single-element list with the corresponding {@link CredentialClaim}
   */
  public static List<CredentialClaim> build(
      String assetId, ProvenanceType provenanceType, String issuer) {
    String predicate = switch (provenanceType) {
      case CREATION -> PROV_WAS_GENERATED_BY;
      case DERIVATION -> PROV_WAS_DERIVED_FROM;
      case ATTRIBUTION -> PROV_WAS_ATTRIBUTED_TO;
      case MODIFICATION -> PROV_WAS_REVISION_OF;
      case APPROVAL -> PROV_WAS_ASSOCIATED_WITH;
    };
    CredentialClaim triple = new CredentialClaim(
        "<" + assetId + ">",
        predicate,
        "<" + issuer + ">");
    return List.of(triple);
  }
}
