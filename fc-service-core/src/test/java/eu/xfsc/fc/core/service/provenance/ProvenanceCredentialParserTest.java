package eu.xfsc.fc.core.service.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvenanceCredentialParserTest {

  private static final String PROV_NS = "http://www.w3.org/ns/prov#";
  private static final String SUBJECT_ID = "did:web:example:asset:v1";
  private static final String OBJECT_VALUE = "did:web:example:activity";

  @Mock
  private VerificationService verificationService;

  private ProvenanceCredentialParser parser;

  @BeforeEach
  void setUp() {
    parser = new ProvenanceCredentialParser(verificationService, new ObjectMapper());
  }

  // --- extractProvenance: compact predicates ---

  static Stream<Arguments> compactPredicates() {
    return Stream.of(
        Arguments.of("prov:wasGeneratedBy", ProvenanceType.CREATION),
        Arguments.of("prov:wasDerivedFrom", ProvenanceType.DERIVATION),
        Arguments.of("prov:wasAttributedTo", ProvenanceType.ATTRIBUTION),
        Arguments.of("prov:wasRevisionOf", ProvenanceType.MODIFICATION)
    );
  }

  @ParameterizedTest
  @MethodSource("compactPredicates")
  void extractProvenance_compactPredicate_returnsCorrectType(String predicate, ProvenanceType expected) {
    String vc = vcWithPredicate(predicate, OBJECT_VALUE);

    ProvenanceInfo result = parser.extractProvenance(vc);

    assertEquals(expected, result.type());
    assertEquals(OBJECT_VALUE, result.objectValue());
  }

  // --- extractProvenance: full IRI predicates ---

  static Stream<Arguments> fullIriPredicates() {
    return Stream.of(
        Arguments.of(PROV_NS + "wasGeneratedBy", ProvenanceType.CREATION),
        Arguments.of(PROV_NS + "wasDerivedFrom", ProvenanceType.DERIVATION),
        Arguments.of(PROV_NS + "wasAttributedTo", ProvenanceType.ATTRIBUTION),
        Arguments.of(PROV_NS + "wasRevisionOf", ProvenanceType.MODIFICATION)
    );
  }

  @ParameterizedTest
  @MethodSource("fullIriPredicates")
  void extractProvenance_fullIriPredicate_returnsCorrectType(String predicate, ProvenanceType expected) {
    String vc = vcWithPredicate(predicate, OBJECT_VALUE);

    ProvenanceInfo result = parser.extractProvenance(vc);

    assertEquals(expected, result.type());
    assertEquals(OBJECT_VALUE, result.objectValue());
  }

  // --- extractProvenance: error paths ---

  @Test
  void extractProvenance_missingCredentialSubject_throwsClientException() {
    String vc = """
        {"id": "did:vc:test"}
        """;

    assertThrows(ClientException.class, () -> parser.extractProvenance(vc));
  }

  @Test
  void extractProvenance_noPovPredicate_throwsClientException() {
    String vc = """
        {
          "credentialSubject": {
            "id": "%s",
            "provenanceType": "CREATION"
          }
        }
        """.formatted(SUBJECT_ID);

    assertThrows(ClientException.class, () -> parser.extractProvenance(vc));
  }

  @Test
  void extractProvenance_predicateWithNullValue_throwsClientException() {
    String vc = """
        {
          "credentialSubject": {
            "id": "%s",
            "prov:wasGeneratedBy": null
          }
        }
        """.formatted(SUBJECT_ID);

    assertThrows(ClientException.class, () -> parser.extractProvenance(vc));
  }

  // --- extractCredentialId ---

  @Test
  void extractCredentialId_present_returnsId() {
    String vc = """
        {"id": "did:vc:test-001", "credentialSubject": {}}
        """;

    assertEquals("did:vc:test-001", parser.extractCredentialId(vc));
  }

  @Test
  void extractCredentialId_absent_returnsNull() {
    String vc = """
        {"credentialSubject": {}}
        """;

    assertNull(parser.extractCredentialId(vc));
  }

  // --- detectFormatLabel ---

  @Test
  void detectFormatLabel_jwtString_returnsJwt() {
    assertEquals("JWT", parser.detectFormatLabel("eyJhbGciOiJFZERTQSJ9.e30.sig"));
  }

  @Test
  void detectFormatLabel_jsonWithContext_returnsJsonld() {
    String vc = """
        {"@context": ["https://www.w3.org/2018/credentials/v1"], "id": "did:vc:x"}
        """;

    assertEquals("JSONLD", parser.detectFormatLabel(vc));
  }

  @Test
  void detectFormatLabel_jsonWithoutContext_returnsJsonldJwt() {
    String vc = """
        {"id": "did:vc:x", "credentialSubject": {}}
        """;

    assertEquals("JSONLD_JWT", parser.detectFormatLabel(vc));
  }

  // --- helpers ---

  private static String vcWithPredicate(String predicate, String objectValue) {
    return """
        {
          "id": "did:vc:test-001",
          "credentialSubject": {
            "id": "%s",
            "%s": "%s"
          }
        }
        """.formatted(SUBJECT_ID, predicate, objectValue);
  }
}
