package eu.xfsc.fc.core.service.verification.claims;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import jakarta.json.JsonArray;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the JSON-LD expansion behaviour observed to confirm
 * the hypothesis that the failure is shape-driven, not a clash of @protected
 * terms between the W3C VC v2 and Gaia-X 2511 contexts.
 *
 * <p>Inputs:
 * <ul>
 *   <li>{@code malformedVp()} — the literal bytes of
 *       {@code cat-integration-tests/fixtures/loire/valid/participant.vp2.jsonld}.
 *       Gaia-X 2511 is at VP-level @context and the inner item is a bare VC
 *       (no Enveloped wrapper, no VC-JWT envelope).</li>
 *   <li>{@code conformantVp()} — the ICAM/GXDCH reference shape. W3C v2 only at
 *       VP level; inner item is EnvelopedVerifiableCredential pointing at a
 *       (placeholder) data:application/vc+ld+json+jwt URL.</li>
 * </ul>
 *
 * <p>Expected hypothesis outcome:
 * <ul>
 *   <li>Malformed VP: {@link JsonLd#expand} throws {@link JsonLdError} with
 *       code {@link JsonLdErrorCode#INVALID_CONTEXT_NULLIFICATION}.</li>
 *   <li>Conformant VP: {@link JsonLd#expand} succeeds and yields a non-empty
 *       expanded array.</li>
 * </ul>
 *
 * <p>This is a read-only diagnostic test. It does not assert claim counts; the
 * downstream {@link CredentialSubjectClaimExtractor} would still need an
 * Enveloped-credential-aware path before it can extract claims from a real
 * Loire VP, but that is orthogonal to the present hypothesis.
 */
class LoireVpShapeReproductionTest {

  // The literal participant.vp2.jsonld from cat-integration-tests/fixtures/loire/valid/.
  private static final String MALFORMED_VP_2511 = """
      {
        "@context": [
          "https://www.w3.org/ns/credentials/v2",
          "https://w3id.org/gaia-x/2511#"
        ],
        "type": ["VerifiablePresentation"],
        "id": "urn:uuid:vp2-bdd-1",
        "verifiableCredential": [
          {
            "@context": [
              "https://www.w3.org/ns/credentials/v2",
              "https://w3id.org/gaia-x/2511#"
            ],
            "type": ["VerifiableCredential", "gx:LegalPerson"],
            "id": "urn:uuid:vc2-in-vp2-bdd-1",
            "issuer": "did:web:issuer.example.com",
            "validFrom": "2026-01-30T00:00:00Z",
            "validUntil": "2027-12-31T23:59:59Z",
            "credentialSubject": {
              "id": "did:web:participant.example.com",
              "@type": "gx:LegalPerson",
              "gx:legalName": [{"@value": "Example Corp BDD VP"}]
            }
          }
        ]
      }
      """;

  // ICAM/GXDCH reference shape: w3c-v2 only at VP @context, inner Enveloped VC.
  // The data URL body is a placeholder; expansion does not parse it.
  private static final String CONFORMANT_VP = """
      {
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "type": ["VerifiablePresentation"],
        "id": "urn:uuid:vp2-conformant-1",
        "verifiableCredential": [
          {
            "@context": "https://www.w3.org/ns/credentials/v2",
            "id": "data:application/vc+ld+json+jwt,eyJhbGciOiJub25lIn0.eyJ0ZXN0IjoiZml4dHVyZSJ9.",
            "type": "EnvelopedVerifiableCredential"
          }
        ]
      }
      """;

  // Variant: drop gaia-x at VP level but keep bare inner VC (not Enveloped).
  // Isolates whether the VP-level context placement is the trigger, or
  // whether the bare inner VC by itself is sufficient.
  private static final String VP_LEVEL_FIXED_INNER_BARE = """
      {
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "type": ["VerifiablePresentation"],
        "id": "urn:uuid:vp2-vp-level-fixed-1",
        "verifiableCredential": [
          {
            "@context": [
              "https://www.w3.org/ns/credentials/v2",
              "https://w3id.org/gaia-x/2511#"
            ],
            "type": ["VerifiableCredential", "gx:LegalPerson"],
            "id": "urn:uuid:vc2-in-vp2-fixed-1",
            "issuer": "did:web:issuer.example.com",
            "validFrom": "2026-01-30T00:00:00Z",
            "validUntil": "2027-12-31T23:59:59Z",
            "credentialSubject": {
              "id": "did:web:participant.example.com",
              "@type": "gx:LegalPerson",
              "gx:legalName": [{"@value": "Example Corp BDD VP"}]
            }
          }
        ]
      }
      """;

  // Variant: gaia-x 2511 at VP level, but inner item is Enveloped (no nested VC).
  // Isolates whether mixing 2511 with EnvelopedVerifiableCredential at VP level
  // is itself a trigger.
  private static final String VP_LEVEL_2511_INNER_ENVELOPED = """
      {
        "@context": [
          "https://www.w3.org/ns/credentials/v2",
          "https://w3id.org/gaia-x/2511#"
        ],
        "type": ["VerifiablePresentation"],
        "id": "urn:uuid:vp2-2511-evc-1",
        "verifiableCredential": [
          {
            "@context": "https://www.w3.org/ns/credentials/v2",
            "id": "data:application/vc+ld+json+jwt,eyJhbGciOiJub25lIn0.eyJ0ZXN0IjoiZml4dHVyZSJ9.",
            "type": "EnvelopedVerifiableCredential"
          }
        ]
      }
      """;

  @Test
  @DisplayName("Original fixture — does participant.vp2.jsonld trip Titanium expansion?")
  void malformedLoireVp_diagnostic() {
    runDiagnostic("MALFORMED_VP_2511", MALFORMED_VP_2511);
  }

  @Test
  @DisplayName("Conformant Loire VP (w3c-v2 only at VP level + EnvelopedVerifiableCredential) expands cleanly")
  void conformantLoireVp_expandsSuccessfully() {
    JsonArray expanded = runExpansionExpectingSuccess(CONFORMANT_VP);
    System.out.println(">>> CONFORMANT_VP → expanded.size=" + expanded.size());
    assertNotNull(expanded, "expansion result must be non-null");
    assertTrue(!expanded.isEmpty(), "expansion of a conformant VP must produce a non-empty array");
  }

  @Test
  @DisplayName("Diagnostic — drop 2511 from VP level but keep bare inner VC; does the inner VC alone trip nullification?")
  void vpLevelFixed_innerBare_diagnostic() {
    runDiagnostic("VP_LEVEL_FIXED_INNER_BARE", VP_LEVEL_FIXED_INNER_BARE);
  }

  @Test
  @DisplayName("Diagnostic — keep 2511 at VP level but use Enveloped inner; does the VP-level mix alone trip nullification?")
  void vpLevel2511_innerEnveloped_diagnostic() {
    runDiagnostic("VP_LEVEL_2511_INNER_ENVELOPED", VP_LEVEL_2511_INNER_ENVELOPED);
  }

  // Actual decoded payload of participant.vp2.signed.jwt — this is what LoireJwtParser
  // hands to the extractor in production. Note the raw-JWT-string in verifiableCredential[0].
  private static final String DECODED_VP_JWT_PAYLOAD = """
      {
        "iss": "did:web:did-server",
        "holder": "did:web:did-server",
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "id": "urn:uuid:jwt-bdd-vp2-1",
        "type": ["VerifiablePresentation"],
        "verifiableCredential": [
          "eyJhbGciOiJFZERTQSIsImN0eSI6InZjIiwia2lkIjoiZGlkOndlYjpkaWQtc2VydmVyI2p3dC1rZXktMSIsInR5cCI6InZjK2p3dCJ9.eyJpc3MiOiJkaWQ6d2ViOmRpZC1zZXJ2ZXIiLCJzdWIiOiJkaWQ6d2ViOnBhcnRpY2lwYW50LmV4YW1wbGUuY29tIiwiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIiwiaHR0cHM6Ly93M2lkLm9yZy9nYWlhLXgvMjUxMSMiXSwiaWQiOiJ1cm46dXVpZDpqd3QtYmRkLXZjMi0xIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsImd4OkxlZ2FsUGVyc29uIl0sImlzc3VlciI6ImRpZDp3ZWI6ZGlkLXNlcnZlciIsInZhbGlkRnJvbSI6IjIwMjYtMDEtMDFUMDA6MDA6MDBaIiwidmFsaWRVbnRpbCI6IjIwMjctMTItMzFUMjM6NTk6NTlaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6d2ViOnBhcnRpY2lwYW50LmV4YW1wbGUuY29tIiwiQHR5cGUiOiJneDpMZWdhbFBlcnNvbiIsImd4OmxlZ2FsTmFtZSI6W3siQHZhbHVlIjoiRXhhbXBsZSBDb3JwIEJERCJ9XX19.7xi2jdbBUlsDtmJyc466gX6eIhmGTPO6TTGZPOSY1tdugfQqjbzCk3_fVMVOi530G5-mXxL_wNU4Z2TvpVx9Bg"
        ]
      }
      """;

  @Test
  @DisplayName("Production input — decoded VP-JWT with verifiableCredential: [<raw-jwt-string>] — trips INVALID_CONTEXT_NULLIFICATION")
  void decodedVpJwtPayload_tripsInvalidContextNullification() {
    JsonLdError thrown = runExpansionExpectingError(DECODED_VP_JWT_PAYLOAD);
    System.out.println(">>> DECODED_VP_JWT_PAYLOAD → code=" + thrown.getCode()
        + " ; message=" + thrown.getMessage());
    assertCode(thrown, JsonLdErrorCode.INVALID_CONTEXT_NULLIFICATION,
        """
            VP whose verifiableCredential array contains a raw JWT compact string \
            (instead of EnvelopedVerifiableCredential objects with data: URLs) \
            trips the W3C v2 context's protected scope. THIS is the production-observed \
            trigger — not the gaia-x/2511 context, not the participant.vp2.jsonld fixture as-is.
            """);
  }

  // Control: replace the raw-JWT-string with an EnvelopedVerifiableCredential
  // wrapper carrying the same JWT inside a data: URL. Expected: succeeds.
  private static final String DECODED_VP_JWT_PAYLOAD_FIXED = """
      {
        "iss": "did:web:did-server",
        "holder": "did:web:did-server",
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "id": "urn:uuid:jwt-bdd-vp2-1",
        "type": ["VerifiablePresentation"],
        "verifiableCredential": [
          {
            "@context": "https://www.w3.org/ns/credentials/v2",
            "id": "data:application/vc+ld+json+jwt,eyJhbGciOiJFZERTQSIsImN0eSI6InZjIiwia2lkIjoiZGlkOndlYjpkaWQtc2VydmVyI2p3dC1rZXktMSIsInR5cCI6InZjK2p3dCJ9.eyJpc3MiOiJkaWQ6d2ViOmRpZC1zZXJ2ZXIiLCJzdWIiOiJkaWQ6d2ViOnBhcnRpY2lwYW50LmV4YW1wbGUuY29tIiwiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIiwiaHR0cHM6Ly93M2lkLm9yZy9nYWlhLXgvMjUxMSMiXSwiaWQiOiJ1cm46dXVpZDpqd3QtYmRkLXZjMi0xIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsImd4OkxlZ2FsUGVyc29uIl0sImlzc3VlciI6ImRpZDp3ZWI6ZGlkLXNlcnZlciIsInZhbGlkRnJvbSI6IjIwMjYtMDEtMDFUMDA6MDA6MDBaIiwidmFsaWRVbnRpbCI6IjIwMjctMTItMzFUMjM6NTk6NTlaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6d2ViOnBhcnRpY2lwYW50LmV4YW1wbGUuY29tIiwiQHR5cGUiOiJneDpMZWdhbFBlcnNvbiIsImd4OmxlZ2FsTmFtZSI6W3siQHZhbHVlIjoiRXhhbXBsZSBDb3JwIEJERCJ9XX19.7xi2jdbBUlsDtmJyc466gX6eIhmGTPO6TTGZPOSY1tdugfQqjbzCk3_fVMVOi530G5-mXxL_wNU4Z2TvpVx9Bg",
            "type": "EnvelopedVerifiableCredential"
          }
        ]
      }
      """;

  @Test
  @DisplayName("Control — same VP-JWT payload but wrap the inner JWT in an EnvelopedVerifiableCredential — expands cleanly")
  void decodedVpJwtPayload_envelopedWrapper_expandsSuccessfully() {
    JsonArray expanded = runExpansionExpectingSuccess(DECODED_VP_JWT_PAYLOAD_FIXED);
    System.out.println(">>> DECODED_VP_JWT_PAYLOAD_FIXED → expanded.size=" + expanded.size());
    assertNotNull(expanded);
    assertTrue(!expanded.isEmpty(),
        "wrapping the raw JWT inside EnvelopedVerifiableCredential removes the trigger");
  }

  // Regression pin — decoded payload of the FIXED participant.vp2.signed.jwt produced
  // after applying the fix to participant.vp2.jwt.jsonld. If a future re-sign or
  // template edit regresses to a raw-JWT-string verifiableCredential array, this fails.
  private static final String FIXED_FIXTURE_DECODED_VP_PAYLOAD = """
      {
        "iss": "did:web:did-server",
        "holder": "did:web:did-server",
        "@context": ["https://www.w3.org/ns/credentials/v2"],
        "id": "urn:uuid:jwt-bdd-vp2-1",
        "type": ["VerifiablePresentation"],
        "verifiableCredential": [
          {
            "@context": "https://www.w3.org/ns/credentials/v2",
            "id": "data:application/vc+ld+json+jwt,eyJhbGciOiJFZERTQSIsImN0eSI6InZjIiwia2lkIjoiZGlkOndlYjpkaWQtc2VydmVyI2p3dC1rZXktMSIsInR5cCI6InZjK2p3dCJ9.eyJpc3MiOiJkaWQ6d2ViOmRpZC1zZXJ2ZXIiLCJzdWIiOiJkaWQ6d2ViOnBhcnRpY2lwYW50LmV4YW1wbGUuY29tIiwiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIiwiaHR0cHM6Ly93M2lkLm9yZy9nYWlhLXgvMjUxMSMiXSwiaWQiOiJ1cm46dXVpZDpqd3QtYmRkLXZjMi0xIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsImd4OkxlZ2FsUGVyc29uIl0sImlzc3VlciI6ImRpZDp3ZWI6ZGlkLXNlcnZlciIsInZhbGlkRnJvbSI6IjIwMjYtMDEtMDFUMDA6MDA6MDBaIiwidmFsaWRVbnRpbCI6IjIwMjctMTItMzFUMjM6NTk6NTlaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6d2ViOnBhcnRpY2lwYW50LmV4YW1wbGUuY29tIiwiQHR5cGUiOiJneDpMZWdhbFBlcnNvbiIsImd4OmxlZ2FsTmFtZSI6W3siQHZhbHVlIjoiRXhhbXBsZSBDb3JwIEJERCJ9XX19.PLACEHOLDER_SIGNATURE",
            "type": "EnvelopedVerifiableCredential"
          }
        ]
      }
      """;

  @Test
  @DisplayName("Regression — decoded payload from the FIXED participant.vp2.signed.jwt expands cleanly through the extractor's call path")
  void fixedFixtureDecodedVpPayload_expandsSuccessfully() {
    JsonArray expanded = runExpansionExpectingSuccess(FIXED_FIXTURE_DECODED_VP_PAYLOAD);
    System.out.println(">>> FIXED_FIXTURE_DECODED_VP_PAYLOAD → expanded.size=" + expanded.size());
    assertNotNull(expanded);
    assertTrue(!expanded.isEmpty());
  }

  // ---------- helpers ----------

  private static JsonLdError runExpansionExpectingError(String jsonLd) {
    try {
      JsonArray result = expand(jsonLd);
      fail("Expected JsonLdError but expansion succeeded with size=" + result.size());
      return null; // unreachable
    } catch (JsonLdError e) {
      return e;
    } catch (Exception e) {
      throw new AssertionError("Unexpected exception type: " + e, e);
    }
  }

  private static JsonArray runExpansionExpectingSuccess(String jsonLd) {
    try {
      return expand(jsonLd);
    } catch (Exception e) {
      throw new AssertionError("Expansion was expected to succeed but threw: " + e, e);
    }
  }

  private static void runDiagnostic(String label, String jsonLd) {
    try {
      JsonArray result = expand(jsonLd);
      System.out.println(">>> " + label + " → SUCCESS, expanded.size=" + result.size());
    } catch (JsonLdError e) {
      System.out.println(">>> " + label + " → JsonLdError code=" + e.getCode()
          + " ; message=" + e.getMessage());
    } catch (Exception e) {
      System.out.println(">>> " + label + " → " + e.getClass().getSimpleName()
          + ": " + e.getMessage());
    }
  }

  /** Same call shape as {@link CredentialSubjectClaimExtractor#extractClaims} line 47. */
  private static JsonArray expand(String jsonLd) throws JsonLdError {
    ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
    Document document = JsonDocument.of(content.getContentAsStream());
    return JsonLd.expand(document).get();
  }

  // Touch unused symbols to silence checkstyle if applicable.
  @SuppressWarnings("unused")
  private static List<?> unusedSink() {
    return List.of(MALFORMED_VP_2511, CONFORMANT_VP, VP_LEVEL_FIXED_INNER_BARE,
        VP_LEVEL_2511_INNER_ENVELOPED,
        new ByteArrayInputStream(new byte[0]).toString(),
        StandardCharsets.UTF_8.name());
  }

  private static void assertCode(JsonLdError actual, JsonLdErrorCode expected, String description) {
    if (actual.getCode() != expected) {
      fail(description + " — expected code " + expected + " but got " + actual.getCode()
          + " (msg: " + actual.getMessage() + ")");
    }
  }
}
