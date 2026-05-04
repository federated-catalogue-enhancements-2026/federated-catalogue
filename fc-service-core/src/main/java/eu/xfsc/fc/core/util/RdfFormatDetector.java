package eu.xfsc.fc.core.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.jena.riot.Lang;

/**
 * Detects the RDF serialization format from an HTTP Content-Type header and/or content inspection.
 *
 * <p>Content-type is checked first (permissive {@code contains()} matching, handles charset params).
 * If the content type is absent or unrecognised, the raw content string is inspected by prefix.
 * Defaults to {@link Lang#NT} when neither check produces a match.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RdfFormatDetector {

  /**
   * Detects the RDF format from content-type and optional content inspection.
   *
   * @param contentType HTTP Content-Type value, or {@code null}
   * @param rawContent  raw content string for fallback inspection; {@code null} skips inspection
   * @return the detected {@link Lang}; defaults to {@link Lang#NT}
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
      if (rawContent.startsWith("{")) {
        return Lang.JSONLD11;
      }
      if (rawContent.startsWith("@prefix") || rawContent.startsWith("@base")) {
        return Lang.TURTLE;
      }
      if (rawContent.startsWith("<?xml") || rawContent.startsWith("<rdf:RDF")) {
        return Lang.RDFXML;
      }
    }
    return Lang.NT;
  }
}
