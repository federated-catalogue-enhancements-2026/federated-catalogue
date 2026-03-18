package eu.xfsc.fc.core.service.assetstore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.exception.ClientException;

/**
 * Validates IRI formats accepted as asset identifiers.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>{@code did:{method}:{method-specific-id}} — Decentralized Identifiers (DID Core spec)</li>
 *   <li>{@code urn:uuid:{uuid-v4}} — UUID URNs per RFC 4122</li>
 *   <li>{@code urn:{nid}:{nss}} — Generic URN syntax per RFC 8141</li>
 *   <li>{@code http://...} or {@code https://...} — HTTP IRIs</li>
 * </ul>
 */
@Component
public class IriValidator {

    // DID syntax: did:method-name:method-specific-id
    // method-name = 1*methodchar, methodchar = %x61-7A / DIGIT (lowercase + digits)
    // method-specific-id allows alphanumeric, dots, dashes, underscores, percent-encoded, colons
    private static final Pattern DID_PATTERN =
        Pattern.compile("^did:[a-z0-9]+:[a-zA-Z0-9._:%-]+$");

    // UUID v4 URN: urn:uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    private static final Pattern UUID_URN_PATTERN =
        Pattern.compile("^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                        Pattern.CASE_INSENSITIVE);

    // Generic URN: urn:nid:nss (nid = 2-32 alphanumeric/hyphens, nss = at least 1 char)
    private static final Pattern URN_PATTERN =
        Pattern.compile("^urn:[a-zA-Z0-9][a-zA-Z0-9-]{0,31}:.+$");

    /**
     * Validate that the given string is an accepted IRI format.
     *
     * @param iri the IRI to validate
     * @throws ClientException if the IRI format is not recognized
     */
    public void validate(String iri) {
        if (iri == null || iri.isBlank()) {
            throw new ClientException("Asset IRI must not be null or blank");
        }

        if (iri.startsWith("did:")) {
            validateDid(iri);
        } else if (iri.startsWith("urn:uuid:")) {
            validateUuidUrn(iri);
        } else if (iri.startsWith("urn:")) {
            validateUrn(iri);
        } else if (iri.startsWith("http://") || iri.startsWith("https://")) {
            validateHttpIri(iri);
            return;
        } else {
            throw new ClientException("Unrecognized IRI format: " + iri
                + ". Expected DID (did:...), URN (urn:...), or HTTP IRI (http(s)://...)");
        }
    }

    private void validateDid(String did) {
        if (!DID_PATTERN.matcher(did).matches()) {
            throw new ClientException("Invalid DID format: " + did
                + ". Expected: did:{method}:{method-specific-id}");
        }
    }

    private void validateUuidUrn(String urn) {
        if (!UUID_URN_PATTERN.matcher(urn).matches()) {
            throw new ClientException("Invalid UUID URN format: " + urn
                + ". Expected: urn:uuid:{8-4-4-4-12 hex}");
        }
    }

    private void validateUrn(String urn) {
        if (!URN_PATTERN.matcher(urn).matches()) {
            throw new ClientException("Invalid URN format: " + urn
                + ". Expected: urn:{namespace-id}:{namespace-specific-string}");
        }
    }

    private void validateHttpIri(String iri) {
        try {
            URI uri = new URI(iri);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ClientException("Invalid HTTP IRI: " + iri + ". Missing host.");
            }
        } catch (URISyntaxException e) {
            throw new ClientException("Invalid HTTP IRI: " + iri + ". " + e.getMessage());
        }
    }
}
