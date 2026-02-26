package eu.xfsc.fc.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.exception.UnsupportedQueryLanguageException;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Validates that the requested query language is supported by the active graph store backend.
 * Throws structured exceptions for unsupported languages or disabled graph stores.
 */
@Component
public class QueryLanguageValidator {

  @Autowired
  private GraphStore graphStore;

  /**
   * Validates that the requested query language matches the active backend's supported language.
   *
   * @param requested the query language requested by the client
   * @throws GraphStoreDisabledException if the graph store is disabled (dummy backend)
   * @throws UnsupportedQueryLanguageException if the requested language does not match the supported language
   */
  public void validateLanguageSupport(QueryLanguage requested) {
    QueryLanguage supported = graphStore.getSupportedQueryLanguage()
        .orElseThrow(() -> new GraphStoreDisabledException(
            "Graph database is disabled. Query functionality is not available."));
    if (supported != requested) {
      String backend = graphStore.getBackendType().name();
      String expectedContentType = getContentType(supported);
      String displayName = getDisplayName(supported);
      String hint = "Please use " + displayName + " queries with Content-Type: " + expectedContentType;
      throw new UnsupportedQueryLanguageException(
          backend, supported.name(), requested.name(), expectedContentType, hint);
    }
  }

  // These helpers live here rather than on QueryLanguage because that enum is
  // OpenAPI-generated code (fc-service-api) and would be overwritten on regeneration.

  /**
   * Returns the Content-Type string associated with the given query language.
   *
   * @param language the query language
   * @return the corresponding Content-Type
   */
  static String getContentType(QueryLanguage language) {
    return switch (language) {
      case OPENCYPHER -> "application/opencypher-query";
      case SPARQL -> "application/sparql-query";
      default -> throw new IllegalArgumentException("No content type mapping for " + language);
    };
  }

  /**
   * Returns a human-readable display name for the given query language.
   *
   * @param language the query language
   * @return the display name
   */
  static String getDisplayName(QueryLanguage language) {
    return switch (language) {
      case OPENCYPHER -> "openCypher";
      case SPARQL -> "SPARQL";
      default -> throw new IllegalArgumentException("No display name mapping for " + language);
    };
  }
}