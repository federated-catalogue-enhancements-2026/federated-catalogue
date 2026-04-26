package eu.xfsc.fc.core.service.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parses and validates raw W3C VC payloads (JSON-LD and JWT forms) for provenance processing.
 */
@Component
@RequiredArgsConstructor
public class ProvenanceCredentialParser {

  private static final String CREDENTIAL_SUBJECT_KEY = "credentialSubject";
  private static final String VC_ID_KEY = "id";
  private static final String CONTEXT_KEY = "@context";
  private static final String PROV_NS = "http://www.w3.org/ns/prov#";
  private static final String ACCEPTED_PREDICATES =
      "prov:wasGeneratedBy, prov:wasDerivedFrom, prov:wasAttributedTo, prov:wasRevisionOf";

  /**
   * Recognizes both compact ({@code prov:X}) and expanded ({@code http://www.w3.org/ns/prov#X})
   * forms of the four supported PROV-O predicates.
   */
  private static final Map<String, ProvenanceType> PREDICATE_MAP = Map.of(
      "prov:wasGeneratedBy", ProvenanceType.CREATION,
      PROV_NS + "wasGeneratedBy", ProvenanceType.CREATION,
      "prov:wasDerivedFrom", ProvenanceType.DERIVATION,
      PROV_NS + "wasDerivedFrom", ProvenanceType.DERIVATION,
      "prov:wasAttributedTo", ProvenanceType.ATTRIBUTION,
      PROV_NS + "wasAttributedTo", ProvenanceType.ATTRIBUTION,
      "prov:wasRevisionOf", ProvenanceType.MODIFICATION,
      PROV_NS + "wasRevisionOf", ProvenanceType.MODIFICATION
  );

  private final VerificationService verificationService;
  private final ObjectMapper objectMapper;

  /**
   * Verifies the raw VC via the verification service.
   *
   * @throws ClientException if the credential fails verification
   */
  public CredentialVerificationResult parseAndValidateVc(String rawVc, String format) {
    try {
      ContentAccessorDirect content = new ContentAccessorDirect(rawVc, format);
      return verificationService.verifyCredential(content);
    } catch (VerificationException ex) {
      throw new ClientException("Invalid provenance credential: " + ex.getMessage(), ex);
    }
  }

  /**
   * Scans {@code credentialSubject} for a supported PROV-O predicate and returns the detected
   * type and the predicate's value, which becomes the PROV-O triple object.
   *
   * <p>Both compact ({@code prov:X}) and expanded IRI ({@code http://www.w3.org/ns/prov#X}) forms
   * are accepted. The first matching predicate wins.</p>
   *
   * @throws ClientException if no supported PROV-O predicate is present
   */
  public ProvenanceInfo extractProvenance(String rawVc) {
    JsonNode root = parseJson(rawVc);
    JsonNode subject = root.path(CREDENTIAL_SUBJECT_KEY);
    if (subject.isMissingNode() || subject.isNull()) {
      throw new ClientException(
          "Provenance credential must contain a 'credentialSubject' with a supported PROV-O predicate. "
              + "Accepted predicates: " + ACCEPTED_PREDICATES);
    }
    for (Map.Entry<String, JsonNode> field : subject.properties()) {
      ProvenanceType type = PREDICATE_MAP.get(field.getKey());
      if (type != null) {
        String objectValue = field.getValue().asText(null);
        if (objectValue == null) {
          throw new ClientException(
              "PROV-O predicate '" + field.getKey() + "' must have a string value.");
        }
        return new ProvenanceInfo(type, objectValue);
      }
    }
    throw new ClientException(
        "credentialSubject must contain one of the supported PROV-O predicates: "
            + ACCEPTED_PREDICATES);
  }

  /**
   * Returns the top-level {@code id} field of the credential, or empty if absent.
   */
  public Optional<String> extractCredentialId(String rawVc) {
    JsonNode root = parseJson(rawVc);
    JsonNode idNode = root.path(VC_ID_KEY);
    return idNode.isTextual() ? Optional.of(idNode.asText()) : Optional.empty();
  }

  /**
   * Detects the serialisation format of the raw VC string.
   *
   * @return {@code "JWT"}, {@code "JSONLD"}, or {@code "JSONLD_JWT"}
   */
  public String detectFormatLabel(String rawVc) {
    String stripped = rawVc.strip();
    if (stripped.startsWith("eyJ")) {
      return "JWT";
    }
    try {
      JsonNode root = objectMapper.readTree(stripped);
      JsonNode context = root.path(CONTEXT_KEY);
      if (!context.isMissingNode()) {
        return "JSONLD";
      }
    } catch (JsonProcessingException ex) {
      throw new ClientException("Unrecognized provenance credential format: not a JWT and not valid JSON", ex);
    }
    return "JSONLD_JWT";
  }

  private JsonNode parseJson(String rawVc) {
    try {
      return objectMapper.readTree(rawVc.strip());
    } catch (JsonProcessingException ex) {
      throw new ClientException("Invalid JSON in provenance credential: " + ex.getMessage(), ex);
    }
  }
}
