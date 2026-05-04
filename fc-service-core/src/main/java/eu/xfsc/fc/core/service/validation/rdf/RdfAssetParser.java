package eu.xfsc.fc.core.service.validation.rdf;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.util.RdfFormatDetector;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Parses RDF asset content and SHACL shapes into Jena {@link Model} instances.
 *
 * <p>Owns the application-scoped {@link StreamManager} backed by a context-cache locator and
 * JSON-LD {@link DocumentLoader}. Handles Loire JWT-wrapped credentials (Case A), plain
 * JSON-LD credentials (Case B), and other RDF serializations (Case C) via
 * {@link RdfFormatDetector} for format detection.</p>
 *
 * <p>Content-type helpers {@link #isJsonLd} and {@link #isRdfXml} are used by validation
 * strategies to determine which assets they are applicable to.</p>
 */
@Slf4j
@Service
public class RdfAssetParser {

  // Loire JWT credentials start with a Base64url-encoded header
  private static final String JWT_PREFIX = "eyJ";
  private static final String JSON_LD_PREFIX = "{";
  private static final String RDF_XML_PREFIX_1 = "<?xml";
  private static final String RDF_XML_PREFIX_2 = "<rdf:RDF";

  private final FileStore fileStore;
  private final DocumentLoader documentLoader;
  private final LoireJwtParser loireJwtParser;

  private StreamManager streamManager;

  /** Creates the parser with context-cache file store, JSON-LD document loader, and JWT unwrapper. */
  public RdfAssetParser(
      @Qualifier("contextCacheFileStore") FileStore fileStore,
      DocumentLoader documentLoader,
      LoireJwtParser loireJwtParser) {
    this.fileStore = fileStore;
    this.documentLoader = documentLoader;
    this.loireJwtParser = loireJwtParser;
  }

  @PostConstruct
  void init() {
    SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
    loader.set("file", documentLoader);
    loader.set("http", documentLoader);
    loader.set("https", documentLoader);
    StreamManager clone = StreamManager.get().clone();
    clone.clearLocators();
    clone.addLocator(new CachingLocator(fileStore));
    streamManager = clone;
  }

  /**
   * Parses an RDF asset into a Jena {@link Model}.
   *
   * <p>Case A — Loire JWT: content starts with {@code "eyJ"}, unwrapped via
   * {@link LoireJwtParser} then parsed as JSON-LD 1.1. Case B — credential JSON-LD: parsed
   * directly. Case C — other RDF: format detected via {@link RdfFormatDetector}.</p>
   *
   * @param asset asset with non-null {@link AssetMetadata#getContentAccessor()}
   * @throws ClientException if the asset has no content or the RDF content is malformed
   */
  public Model parse(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content == null) {
      throw new ClientException(
          "Asset " + asset.getId() + " is not an RDF asset and cannot be validated with SHACL");
    }
    String rawContent = content.getContentAsString().strip();
    if (rawContent.startsWith(JWT_PREFIX)) {
      ContentAccessor unwrapped = loireJwtParser.unwrap(content);
      return parseRdfContent(unwrapped, Lang.JSONLD11);
    }
    Lang lang = RdfFormatDetector.detect(asset.getContentType(), rawContent);
    return parseRdfContent(content, lang);
  }

  /**
   * Parses a SHACL shapes document (Turtle) into a Jena {@link Model}.
   *
   * @param shape ContentAccessor containing a valid SHACL Turtle document
   * @throws ClientException if the Turtle content is malformed
   */
  public Model parseShape(ContentAccessor shape) {
    try {
      Model model = ModelFactory.createDefaultModel();
      RDFParser.create()
          .streamManager(streamManager)
          .source(shape.getContentAsStream())
          .lang(Lang.TURTLE)
          .parse(model);
      return model;
    } catch (RiotException e) {
      throw new ClientException("Invalid SHACL shape: " + e.getMessage(), e);
    }
  }

  /**
   * Returns {@code true} if the asset content is JSON-LD (content starts with '{').
   * Falls back to content-type inspection for assets without a content accessor.
   */
  public boolean isJsonLd(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content != null) {
      return content.getContentAsString().strip().startsWith(JSON_LD_PREFIX);
    }
    String ct = asset.getContentType();
    return ct != null && (ct.contains("application/ld+json")
        || ct.contains("application/vc+ld+json")
        || ct.contains("application/vp+ld+json"));
  }

  /**
   * Returns {@code true} if the asset content is RDF/XML (content starts with {@code "<?xml"}
   * or {@code "<rdf:RDF"}). Falls back to content-type inspection for assets without a content accessor.
   */
  public boolean isRdfXml(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content != null) {
      String raw = content.getContentAsString().strip();
      return raw.startsWith(RDF_XML_PREFIX_1) || raw.startsWith(RDF_XML_PREFIX_2);
    }
    String ct = asset.getContentType();
    return ct != null && ct.contains("rdf+xml");
  }

  /**
   * Returns the shared {@link StreamManager} backed by the context-cache locator.
   * Exposed for use by {@link eu.xfsc.fc.core.service.verification.SchemaValidationServiceImpl}
   * to eliminate its duplicate stream-manager setup.
   */
  public StreamManager getStreamManager() {
    return streamManager;
  }

  private Model parseRdfContent(ContentAccessor content, Lang lang) {
    try {
      Model model = ModelFactory.createDefaultModel();
      RDFParser.create()
          .streamManager(streamManager)
          .source(content.getContentAsStream())
          .lang(lang)
          .parse(model);
      return model;
    } catch (RiotException e) {
      throw new ClientException("Invalid RDF asset content: " + e.getMessage(), e);
    }
  }
}
