package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.pojo.CredentialClaim;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builds PROV-O {@link CredentialClaim} triples for a provenance credential.
 *
 * <p>Maps each {@link ProvenanceType} to its corresponding W3C PROV-O predicate URI, producing a
 * single triple in N-Triples form: {@code <assetId> <prov:predicate> <objectValue> .}</p>
 *
 * <p>The triple subject is the versioned asset identifier ({@code assetId:vN}), the predicate is
 * the PROV-O URI, and the object is the value of that predicate as declared in the VC's
 * {@code credentialSubject} (e.g. an agent DID, source entity IRI, or activity IRI).</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProvOTripleBuilder {

  private static final String PROV_NS = "http://www.w3.org/ns/prov#";

  private static final String PROV_WAS_GENERATED_BY = "<" + PROV_NS + "wasGeneratedBy>";
  private static final String PROV_WAS_DERIVED_FROM = "<" + PROV_NS + "wasDerivedFrom>";
  private static final String PROV_WAS_ATTRIBUTED_TO = "<" + PROV_NS + "wasAttributedTo>";
  private static final String PROV_WAS_REVISION_OF = "<" + PROV_NS + "wasRevisionOf>";

  /**
   * Builds a list containing the single PROV-O triple for the given provenance credential.
   *
   * @param assetId        versioned asset identifier used as the triple subject (e.g. {@code did:example:abc:v1})
   * @param provenanceType the provenance relation type to map to a PROV-O predicate
   * @param objectValue    IRI value of the PROV-O predicate from {@code credentialSubject}
   * @return a single-element list with the corresponding {@link CredentialClaim}
   */
  public static List<CredentialClaim> build(
      String assetId, ProvenanceType provenanceType, String objectValue) {
    String predicate = switch (provenanceType) {
      case CREATION -> PROV_WAS_GENERATED_BY;
      case DERIVATION -> PROV_WAS_DERIVED_FROM;
      case ATTRIBUTION -> PROV_WAS_ATTRIBUTED_TO;
      case MODIFICATION -> PROV_WAS_REVISION_OF;
    };
    CredentialClaim triple = new CredentialClaim(
        "<" + assetId + ">",
        predicate,
        "<" + objectValue + ">");
    return List.of(triple);
  }
}
