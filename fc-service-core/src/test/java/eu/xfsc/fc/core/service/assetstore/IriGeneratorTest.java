package eu.xfsc.fc.core.service.assetstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

class IriGeneratorTest {

    private static final Pattern UUID_URN_PATTERN =
        Pattern.compile("^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private IriGenerator iriGenerator;

    @BeforeEach
    void setUp() {
        iriGenerator = new IriGenerator(new IriValidator(), new ObjectMapper());
    }

    // --- UUID URN generation ---

    @Test
    void shouldGenerateValidUuidUrn() {
        String urn = iriGenerator.generateUuidUrn();
        assertNotNull(urn);
        assertTrue(urn.startsWith("urn:uuid:"));
        assertTrue(UUID_URN_PATTERN.matcher(urn).matches(),
                "Generated URN should match RFC 4122 format: " + urn);
    }

    @Test
    void shouldGenerateUniqueUrns() {
        String urn1 = iriGenerator.generateUuidUrn();
        String urn2 = iriGenerator.generateUuidUrn();
        assertNotEquals(urn1, urn2);
    }

    // --- DID generation (demonstration per SRS verification) ---

    @Test
    void shouldGenerateDidWeb() {
        String did = iriGenerator.generateDid("web", "example.com");
        assertEquals("did:web:example.com", did);
    }

    @Test
    void shouldGenerateDidKey() {
        String did = iriGenerator.generateDid("key", "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK");
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did);
    }

    // --- RDF ID extraction ---

    @Test
    void shouldExtractIdFromCredentialSubject() {
        String jsonLd = """
                {
                    "@context": ["https://www.w3.org/2018/credentials/v1"],
                    "@type": "VerifiableCredential",
                    "credentialSubject": {
                        "id": "did:web:example.com:participant:123",
                        "name": "Test Participant"
                    }
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("did:web:example.com:participant:123", iri);
    }

    @Test
    void shouldExtractIdFromCredentialSubjectArray() {
        String jsonLd = """
                {
                    "@context": ["https://www.w3.org/2018/credentials/v1"],
                    "credentialSubject": [{
                        "id": "did:web:example.com:service:456",
                        "type": "ServiceOffering"
                    }]
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("did:web:example.com:service:456", iri);
    }

    @Test
    void shouldExtractIdFromJsonLdAtId() {
        String jsonLd = """
                {
                    "@context": "https://www.w3.org/ns/shacl",
                    "@id": "http://example.org/PersonShape",
                    "@type": "sh:NodeShape"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("http://example.org/PersonShape", iri);
    }

    @Test
    void shouldExtractOwlOntologyIri() {
        String jsonLd = """
                {
                    "@id": "http://example.org/MyOntology",
                    "@type": "owl:Ontology",
                    "rdfs:label": "My Ontology"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("http://example.org/MyOntology", iri);
    }

    @Test
    void shouldExtractSkosConceptSchemeIri() {
        String jsonLd = """
                {
                    "@id": "http://example.org/MyScheme",
                    "@type": "skos:ConceptScheme",
                    "skos:prefLabel": "My Scheme"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("http://example.org/MyScheme", iri);
    }

    @Test
    void shouldFallbackToUuidWhenNoIdInRdf() {
        String jsonLd = """
                {
                    "@context": "https://schema.org/",
                    "name": "Something without an ID"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertTrue(iri.startsWith("urn:uuid:"));
    }

    @Test
    void shouldFallbackToUuidWhenContentIsInvalidJson() {
        ContentAccessorDirect content = new ContentAccessorDirect("not json at all");
        String iri = iriGenerator.resolveIri(content, true);
        assertTrue(iri.startsWith("urn:uuid:"));
    }

    // --- Non-RDF resolution ---

    @Test
    void shouldGenerateUuidForNonRdf() {
        String iri = iriGenerator.resolveIri(null, false);
        assertTrue(iri.startsWith("urn:uuid:"));
        assertTrue(UUID_URN_PATTERN.matcher(iri).matches());
    }

    @Test
    void shouldGenerateUuidForNonRdfEvenWithContent() {
        ContentAccessorDirect content = new ContentAccessorDirect("some pdf bytes");
        String iri = iriGenerator.resolveIri(content, false);
        assertTrue(iri.startsWith("urn:uuid:"));
    }

    // --- credentialSubject.id takes priority over @id ---

    @Test
    void credentialSubjectIdTakesPriorityOverAtId() {
        String jsonLd = """
                {
                    "@id": "http://example.org/presentation",
                    "credentialSubject": {
                        "id": "did:web:example.com:priority-id"
                    }
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("did:web:example.com:priority-id", iri);
    }
}
