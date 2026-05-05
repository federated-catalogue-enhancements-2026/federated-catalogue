package eu.xfsc.fc.core.util;

import eu.xfsc.fc.core.exception.ClientException;
import lombok.experimental.UtilityClass;
import org.apache.jena.riot.Lang;

/**
 * Detects the RDF serialization format from an HTTP Content-Type header and/or content inspection.
 *
 * <p>Content-type is checked first (permissive {@code contains()} matching, handles charset params).
 * If the content type is absent or unrecognised, the raw content string is inspected by prefix.
 * Throws {@link eu.xfsc.fc.core.exception.ClientException} when neither check produces a match.</p>
 *
 * <p>Also exposes content prefix constants shared with {@link eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser}.</p>
 */
@UtilityClass
public class CredentialFormatDetector {

  public static final String JWT_PREFIX = "eyJ";
  public static final String JSON_LD_PREFIX = "{";
  public static final String RDF_XML_PREFIX_1 = "<?xml";
  public static final String RDF_XML_PREFIX_2 = "<rdf:RDF";
  static final String TURTLE_PREFIX_1 = "@prefix";
  static final String TURTLE_PREFIX_2 = "@base";

  /**
   * Detects the RDF format from content-type and optional content inspection.
   *
   * @param contentType HTTP Content-Type value, or {@code null}
   * @param rawContent  raw content string for fallback inspection; {@code null} skips inspection
   * @return the detected {@link Lang}
   * @throws ClientException if the format cannot be determined from either the content-type or the content
   */
  public static Lang detect(String contentType, String rawContent) {
    if (contentType != null) {
      if (contentType.contains("text/turtle")) {
        return Lang.TURTLE;
      }
      if (contentType.contains("n-triples")) {
        return Lang.NT;
      }
      if (contentType.contains("rdf+xml")) {
        return Lang.RDFXML;
      }
      if (contentType.contains("application/vc+ld+json") || contentType.contains("application/vp+ld+json")
          || contentType.contains("application/ld+json") || contentType.contains("application/json")) {
        return Lang.JSONLD11;
      }
    }
    if (rawContent != null) {
      if (rawContent.startsWith(JSON_LD_PREFIX)) {
        return Lang.JSONLD11;
      }
      if (rawContent.startsWith(TURTLE_PREFIX_1) || rawContent.startsWith(TURTLE_PREFIX_2)) {
        return Lang.TURTLE;
      }
      if (rawContent.startsWith(RDF_XML_PREFIX_1) || rawContent.startsWith(RDF_XML_PREFIX_2)) {
        return Lang.RDFXML;
      }
      // N-Triples have no keyword prefix. IRI subjects start with '<' (RDF/XML is already matched above),
      // blank-node subjects start with '_:', and comment lines start with '#'.
      if (rawContent.startsWith("<") || rawContent.startsWith("_:") || rawContent.startsWith("#")) {
        return Lang.NT;
      }
    }
    throw new ClientException(
        "Cannot determine RDF format: unrecognised content-type '" + contentType
        + "' and content does not match any known RDF serialization");
  }
}
