package eu.xfsc.fc.core.service.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.verification.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parses and validates raw W3C VC payloads (JSON-LD and JWT forms) for provenance processing.
 */
@Component
@RequiredArgsConstructor
public class ProvenanceCredentialParser {

  private static final String CREDENTIAL_SUBJECT_KEY = "credentialSubject";
  private static final String PROVENANCE_TYPE_KEY = "provenanceType";
  private static final String VC_ID_KEY = "id";
  private static final String CONTEXT_KEY = "@context";
  static final String ACCEPTED_PROVENANCE_TYPES =
      "CREATION, DERIVATION, ATTRIBUTION, MODIFICATION, APPROVAL";

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
   * Extracts and validates the {@code credentialSubject.provenanceType} field.
   *
   * @throws ClientException if the field is absent or not a recognised {@link ProvenanceType}
   */
  public ProvenanceType extractProvenanceType(String rawVc) {
    JsonNode root = parseJson(rawVc);
    JsonNode subject = root.path(CREDENTIAL_SUBJECT_KEY);
    if (subject.isMissingNode() || subject.isNull()) {
      throw new ClientException(
          "Provenance credential must contain a 'credentialSubject' with 'provenanceType'. "
              + "Accepted values: " + ACCEPTED_PROVENANCE_TYPES);
    }
    String typeValue = subject.path(PROVENANCE_TYPE_KEY).asText(null);
    if (typeValue == null) {
      throw new ClientException(
          "credentialSubject.provenanceType is required. Accepted values: " + ACCEPTED_PROVENANCE_TYPES);
    }
    try {
      return ProvenanceType.valueOf(typeValue);
    } catch (IllegalArgumentException ex) {
      throw new ClientException(
          "Unknown provenanceType '" + typeValue + "'. Accepted values: " + ACCEPTED_PROVENANCE_TYPES);
    }
  }

  /**
   * Returns the top-level {@code id} field of the credential, or {@code null} if absent.
   */
  public String extractCredentialId(String rawVc) {
    JsonNode root = parseJson(rawVc);
    JsonNode idNode = root.path(VC_ID_KEY);
    return idNode.isTextual() ? idNode.asText() : null;
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
    } catch (JsonProcessingException ignored) {
      // fall through to JSONLD_JWT
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
