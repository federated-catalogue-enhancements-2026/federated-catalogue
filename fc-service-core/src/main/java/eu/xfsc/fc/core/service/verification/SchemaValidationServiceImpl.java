package eu.xfsc.fc.core.service.verification;

import java.util.List;

import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SemanticValidationResult;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.DanubeTechClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.TitaniumClaimExtractor;
import eu.xfsc.fc.core.util.ClaimValidator;
import lombok.extern.slf4j.Slf4j;

/**
 * (CAT-FR-SF-04) Default implementation of {@link SchemaValidationService}.
 *
 * <p>Performs SHACL validation by extracting RDF claims from a self-description payload
 * and validating them against SHACL shape graphs using {@link ClaimValidator}.</p>
 *
 * @see SchemaValidationService
 */
@Slf4j
@Component
public class SchemaValidationServiceImpl implements SchemaValidationService {

    /**
     * Ordered array of claim extractors tried in sequence until one succeeds.
     * {@link TitaniumClaimExtractor} is tried first (Titanium JSON-LD processor),
     * falling back to {@link DanubeTechClaimExtractor} (Danube Tech LD library).
     */
    private static final ClaimExtractor[] EXTRACTORS = new ClaimExtractor[]{
        new TitaniumClaimExtractor(), new DanubeTechClaimExtractor()
    };

    @Autowired
    private SchemaStore schemaStore;

    @Autowired
    @Qualifier("contextCacheFileStore")
    private FileStore fileStore;

    @Autowired
    private DocumentLoader documentLoader;

    private boolean loadersInitialised;
    private StreamManager streamManager;

    /** {@inheritDoc} Delegates to {@link #validateSelfDescriptionAgainstSchema} with a {@code null} schema. */
    @Override
    public SemanticValidationResult validateSelfDescriptionAgainstCompositeSchema(ContentAccessor payload) {
        return validateSelfDescriptionAgainstSchema(payload, null);
    }

    /** {@inheritDoc} */
    @Override
    public SemanticValidationResult validateSelfDescriptionAgainstSchema(ContentAccessor payload, ContentAccessor schema) {
        log.debug("validateSelfDescriptionAgainstSchema.enter;");
        SemanticValidationResult result = null;
        try {
            if (schema == null) {
                schema = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
            }
            List<SdClaim> claims = extractClaims(payload);
            result = validateClaimsAgainstSchema(claims, schema);
        } catch (Exception exc) {
            log.info("validateSelfDescriptionAgainstSchema.error: {}", exc.getMessage());
        }
        boolean conforms = result != null && result.isConforming();
        log.debug("validateSelfDescriptionAgainstSchema.exit; conforms: {}", conforms);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public SemanticValidationResult validateClaimsAgainstCompositeSchema(List<SdClaim> claims) {
        log.debug("validateClaimsAgainstCompositeSchema.enter;");
        SemanticValidationResult result = null;
        try {
            ContentAccessor shaclShape = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
            result = validateClaimsAgainstSchema(claims, shaclShape);
        } catch (Exception exc) {
            log.info("validateClaimsAgainstCompositeSchema.error: {}", exc.getMessage());
        }
        boolean conforms = result != null && result.isConforming();
        log.debug("validateClaimsAgainstCompositeSchema.exit; conforms: {}", conforms);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public SemanticValidationResult validateClaimsAgainstSchema(List<SdClaim> claims, ContentAccessor schema) {
        String report = ClaimValidator.validateClaimsBySchema(claims, schema, getStreamManager());
        return new SemanticValidationResult(report == null, report);
    }

    /**
     * Extracts RDF claims from a self-description payload by trying each
     * {@link ClaimExtractor} in order. Returns the first successful extraction
     * result, or {@code null} if all extractors fail.
     *
     * @param payload the self-description to extract claims from
     * @return extracted claims, or {@code null} if extraction fails
     */
    private List<SdClaim> extractClaims(ContentAccessor payload) {
        initLoaders();
        List<SdClaim> claims = null;
        for (ClaimExtractor extractor : EXTRACTORS) {
            try {
                claims = extractor.extractClaims(payload);
                if (claims != null) {
                    break;
                }
            } catch (Exception ex) {
                log.error("extractClaims.error using {}: {}", extractor.getClass().getName(), ex.getMessage());
            }
        }
        return claims;
    }

    /**
     * Lazily initialises the JSON-LD {@link SchemeRouter} with the injected
     * {@link DocumentLoader} for {@code file}, {@code http}, and {@code https}
     * schemes. Called once before the first claim extraction.
     */
    private void initLoaders() {
        if (!loadersInitialised) {
            log.debug("initLoaders; Setting up SchemeRouter");
            SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
            loader.set("file", documentLoader);
            loader.set("http", documentLoader);
            loader.set("https", documentLoader);
            loadersInitialised = true;
        }
    }

    /**
     * Returns a lazily initialised Jena {@link StreamManager} configured with
     * a {@link CachingLocator} backed by the context-cache {@link FileStore}.
     * The manager is cloned from the global default and cleared of standard
     * locators so that all resource lookups go through the cache.
     *
     * @return the configured {@link StreamManager} for SHACL validation
     */
    private StreamManager getStreamManager() {
        if (streamManager == null) {
            initLoaders();
            log.debug("getStreamManager; Setting up Jena caching Locator");
            StreamManager clone = StreamManager.get().clone();
            clone.clearLocators();
            clone.addLocator(new CachingLocator(fileStore));
            streamManager = clone;
        }
        return streamManager;
    }
}
