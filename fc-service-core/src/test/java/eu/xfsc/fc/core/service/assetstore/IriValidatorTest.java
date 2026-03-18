package eu.xfsc.fc.core.service.assetstore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.exception.ClientException;

class IriValidatorTest {

    private IriValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IriValidator();
    }

    // --- Valid cases ---

    @Test
    void acceptsValidDidWeb() {
        assertDoesNotThrow(() -> validator.validate("did:web:example.com"));
    }

    @Test
    void acceptsValidDidKey() {
        assertDoesNotThrow(() -> validator.validate("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"));
    }

    @Test
    void acceptsDidWithColonsInMethodSpecificId() {
        assertDoesNotThrow(() -> validator.validate("did:web:example.com:assets:contract-123"));
    }

    @Test
    void acceptsValidUuidUrn() {
        assertDoesNotThrow(() -> validator.validate("urn:uuid:550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void acceptsUuidUrnUpperCase() {
        assertDoesNotThrow(() -> validator.validate("urn:uuid:550E8400-E29B-41D4-A716-446655440000"));
    }

    @Test
    void acceptsGenericUrn() {
        assertDoesNotThrow(() -> validator.validate("urn:isbn:978-3-16-148410-0"));
    }

    @Test
    void acceptsHttpIri() {
        assertDoesNotThrow(() -> validator.validate("http://example.org/MyOntology"));
    }

    @Test
    void acceptsHttpsIri() {
        assertDoesNotThrow(() -> validator.validate("https://example.org/shapes/PersonShape"));
    }

    // --- Invalid cases ---

    @Test
    void rejectsNull() {
        assertThrows(ClientException.class, () -> validator.validate(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(ClientException.class, () -> validator.validate("  "));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(ClientException.class, () -> validator.validate(""));
    }

    @Test
    void rejectsMalformedDid() {
        assertThrows(ClientException.class, () -> validator.validate("did:"));
    }

    @Test
    void rejectsDidWithoutMethodSpecificId() {
        assertThrows(ClientException.class, () -> validator.validate("did:web"));
    }

    @Test
    void rejectsMalformedUuidUrn() {
        assertThrows(ClientException.class, () -> validator.validate("urn:uuid:not-a-uuid"));
    }

    @Test
    void rejectsUuidUrnTooShort() {
        assertThrows(ClientException.class, () -> validator.validate("urn:uuid:550e8400"));
    }

    @Test
    void rejectsUnrecognizedFormat() {
        assertThrows(ClientException.class, () -> validator.validate("foobar123"));
    }

    @Test
    void rejectsPlainUuidWithoutUrnPrefix() {
        assertThrows(ClientException.class, () -> validator.validate("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void rejectsHttpIriWithoutHost() {
        assertThrows(ClientException.class, () -> validator.validate("http://"));
    }

    @Test
    void rejectsHttpIriWithInvalidSyntax() {
        assertThrows(ClientException.class, () -> validator.validate("http:// spaces not allowed"));
    }
}
