package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;
import static eu.xfsc.fc.server.util.SessionUtils.getSessionParticipantId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.assetstore.IriGenerator;
import eu.xfsc.fc.core.service.assetstore.RdfDetector;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service layer for asset uploads. "Asset" is the umbrella term for anything
 * stored in the catalogue. Two paths exist:
 * <ul>
 *   <li><b>RDF-Data - currently only credential data</b> (RDF) — verified via {@link VerificationService}, indexed in the graph store.</li>
 *   <li><b>Non-RDF Asset</b> ("unstructured" data) — stored as-is in the file store, no verification.</li>
 * </ul>
 *
 * @see eu.xfsc.fc.core.service.assetstore.RdfDetector
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetUploadService {

    private final VerificationService verificationService;
    private final AssetStore assetStorePublisher;
    private final RdfDetector rdfDetector;
    private final IriGenerator iriGenerator;
    private final ProtectedNamespaceFilter protectedNamespaceFilter;
    private final GraphStore graphStore;
    private final ObjectMapper objectMapper;

    public UploadResult processUpload(byte[] content, String contentType, String originalFilename) {
        return processUpload(content, contentType, originalFilename, null);
    }

    /**
     * Process an upload, optionally enriching an existing non-RDF asset.
     *
     * @param existingId when non-null, the IRI of an existing active asset to update (new Envers revision);
     *                   when null, a fresh asset is created with a generated IRI
     */
    public UploadResult processUpload(byte[] content, String contentType, String originalFilename, String existingId) {
        if (content == null || content.length == 0) {
            throw new ClientException("Upload content must not be empty");
        }

        if (!rdfDetector.isRdf(contentType, content)) {
            return new UploadResult.AssetCreated(handleNonRdfAsset(content, contentType, originalFilename, existingId));
        }

        // Try enrichment path: check if RDF describes an existing non-RDF asset
        String subjectId = extractSubjectIdFromRdf(content, contentType);
        if (subjectId != null) {
            var existing = assetStorePublisher.findEnrichableAsset(subjectId);
            if (existing.isPresent()) {
                log.debug("processUpload; detected enrichment case for non-RDF asset {}", subjectId);
                return new UploadResult.AssetEnriched(enrichAsset(existing.get(), content, contentType));
            }
        }

        // Not an enrichment case; process as new RDF asset
        return new UploadResult.AssetCreated(handleCredential(content, contentType));
    }

    private AssetMetadata handleCredential(byte[] content, String contentType) {
        log.debug("handleCredential; detected RDF content, delegating to verification pipeline");
        String text = new String(content, StandardCharsets.UTF_8);
        ContentAccessorDirect contentAccessor = new ContentAccessorDirect(text, contentType);

        CredentialVerificationResult verificationResult = verificationService.verifyCredential(contentAccessor);

        // Non-credential RDF: ID and issuer are null — resolve both from local context.
        String assetId = verificationResult.getId() != null
                ? verificationResult.getId()
                : iriGenerator.resolveIri(contentAccessor, true);
        String issuer = verificationResult.getIssuer() != null
                ? verificationResult.getIssuer()
                : getSessionParticipantId();

        AssetMetadata assetMetadata = new AssetMetadata(assetId, issuer, verificationResult.getValidators(), contentAccessor);
        assetMetadata.setContentType(contentType);
        assetMetadata.setFileSize((long) content.length);

        checkParticipantAccess(assetMetadata.getIssuer());
        assetStorePublisher.storeCredential(assetMetadata, verificationResult);

        log.debug("handleCredential.exit; stored credential with hash: {}", assetMetadata.getAssetHash());
        return assetMetadata;
    }

    private AssetMetadata handleNonRdfAsset(byte[] content, String contentType, String originalFilename, String existingId) {
        log.debug("handleNonRdfAsset; non-RDF asset, skipping verification");
        String assetHash = calculateSha256AsHex(content);
        String participantId = getSessionParticipantId();
        Instant now = Instant.now();

        ContentAccessorBinary contentAccessor = new ContentAccessorBinary(content);
        AssetMetadata assetMetadata = new AssetMetadata(assetHash, existingId, AssetStatus.ACTIVE,
                participantId, null, now, now, contentAccessor);
        assetMetadata.setContentType(contentType);
        assetMetadata.setFileSize((long) content.length);

        return assetStorePublisher.storeUnverified(assetMetadata, originalFilename);
    }

    /**
     * Enriches an existing non-RDF asset with RDF metadata.
     *
     * Steps:
     * 1. Parse RDF payload to list of claims
     * 2. Validate at least one claim has the asset's subject IRI
     * 3. Filter protected namespace claims
     * 4. Delete old enrichment triples from graph
     * 5. Write new triples to graph
     * 6. Persist raw RDF payload to asset.content
     * 7. Return enrichment response with counts
     */
    private AssetEnrichmentResponse enrichAsset(AssetRecord record, byte[] rdfPayload, String contentType) {
        String subjectId = record.getId();
        log.debug("enrichAsset.enter; assetId={}, contentType={}", subjectId, contentType);

        // Step 1: Parse RDF payload
        String rawPayloadText = new String(rdfPayload, StandardCharsets.UTF_8);
        List<RdfClaim> claims = parseRdfClaims(rawPayloadText, contentType);
        log.debug("enrichAsset; parsed {} claims", claims.size());

        // Step 2: Validate that at least one claim has the asset's subject IRI
        boolean hasSubjectClaim = claims.stream()
                .anyMatch(c -> subjectId.equals(c.getSubjectValue()));
        if (!hasSubjectClaim && !claims.isEmpty()) {
            throw new ClientException(
                    "RDF payload must contain at least one claim with subject IRI: " + subjectId);
        }

        // Step 3: Filter protected namespaces
        FilteredClaims filteredClaims = protectedNamespaceFilter.filterClaims(claims, "asset enrichment");
        int rejectedCount = claims.size() - filteredClaims.claims().size();
        log.debug("enrichAsset; filtered {} claims, {} remaining", rejectedCount, filteredClaims.claims().size());

        // Step 4: Delete old enrichment triples
        graphStore.deleteClaims(subjectId);
        log.debug("enrichAsset; deleted old triples for subject {}", subjectId);

        // Step 5: Write new triples to graph
        graphStore.addClaims(filteredClaims.claims(), subjectId);
        log.debug("enrichAsset; added {} new triples", filteredClaims.claims().size());

        // Step 6: Persist raw RDF payload (unfiltered, for GD-07 rebuild)
        assetStorePublisher.saveEnrichedContent(subjectId, rawPayloadText);
        log.debug("enrichAsset; persisted enrichment content, asset updated");

        // Step 7: Return enrichment response
        AssetEnrichmentResponse response = new AssetEnrichmentResponse(
                subjectId,
                filteredClaims.claims().size(),
                rejectedCount,
                Instant.now()
        );
        log.debug("enrichAsset.exit; triplesAdded={}, triplesRejected={}", response.getTriplesAdded(), response.getTriplesRejected());
        return response;
    }

    /**
     * Extracts the subject IRI from an RDF payload.
     * Returns the first subject found; null if parsing fails or RDF is empty.
     */
    private String extractSubjectIdFromRdf(byte[] rdfPayload, String contentType) {
        try {
            String text = new String(rdfPayload, StandardCharsets.UTF_8);
            Lang lang = detectLang(contentType);
            Model model = ModelFactory.createDefaultModel();
            RDFParser.fromString(text, lang).parse(model);

            // Return the first subject found
            if (model.listSubjects().hasNext()) {
                Resource subj = model.listSubjects().next();
                return subj.getURI();
            }
            return null;
        } catch (RiotException ex) {
            log.debug("extractSubjectIdFromRdf; parsing failed: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Parses RDF claims from a payload string.
     * Supports JSON-LD, Turtle, N-Triples, and RDF/XML with fallback.
     */
    private List<RdfClaim> parseRdfClaims(String rdfPayload, String contentType) {
        Lang lang = detectLang(contentType);
        Model model = parseWithFallback(rdfPayload, lang);
        List<RdfClaim> claims = new ArrayList<>();

        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            claims.add(new RdfClaim(it.nextStatement(), objectMapper));
        }

        return claims;
    }

    /**
     * Parses RDF with fallback sequence: primary lang, then JSON-LD, Turtle, N-Triples, RDF/XML.
     */
    private Model parseWithFallback(String rdfPayload, Lang primaryLang) {
        Lang[] fallbackSequence = buildLangSequence(primaryLang);
        RiotException lastException = null;

        for (Lang lang : fallbackSequence) {
            try {
                assertNoDoctype(rdfPayload, lang);
                Model model = ModelFactory.createDefaultModel();
                RDFParser.fromString(rdfPayload, lang).parse(model);
                return model;
            } catch (RiotException ex) {
                log.debug("parseWithFallback; lang {} failed: {}", lang, ex.getMessage());
                lastException = ex;
            }
        }

        throw lastException != null ? lastException
                : new RiotException("RDF parsing failed: no supported format matched");
    }

    /**
     * Builds fallback sequence with primary lang first, then remaining langs.
     */
    private Lang[] buildLangSequence(Lang primaryLang) {
        List<Lang> sequence = new ArrayList<>();
        sequence.add(primaryLang);
        for (Lang lang : new Lang[]{Lang.JSONLD, Lang.TURTLE, Lang.NTRIPLES, Lang.RDFXML}) {
            if (lang != primaryLang) {
                sequence.add(lang);
            }
        }
        return sequence.toArray(new Lang[0]);
    }

    /**
     * XXE prevention: rejects RDF/XML with DOCTYPE declarations.
     */
    private static void assertNoDoctype(String rdfPayload, Lang lang) {
        if (lang == Lang.RDFXML && rdfPayload.contains("<!DOCTYPE")) {
            throw new RiotException("RDF/XML input must not contain DOCTYPE declarations");
        }
    }

    /**
     * Maps MIME content type to Jena Lang. Defaults to JSON-LD for null/unknown types.
     */
    private static Lang detectLang(String contentType) {
        if (contentType == null) {
            return Lang.JSONLD;
        }
        return switch (contentType.strip().toLowerCase()) {
            case VerificationConstants.MEDIA_TYPE_TURTLE -> Lang.TURTLE;
            case VerificationConstants.MEDIA_TYPE_NTRIPLES -> Lang.NTRIPLES;
            case VerificationConstants.MEDIA_TYPE_RDF_XML -> Lang.RDFXML;
            default -> Lang.JSONLD;
        };
    }
}