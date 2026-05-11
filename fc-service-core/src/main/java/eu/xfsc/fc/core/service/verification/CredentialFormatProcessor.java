package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.Validator;

import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Strategy interface for a credential format family. Implementations own the entire
 * lifecycle for one format: detection, JWT determination, JSON-LD unwrap, and any
 * format-specific policy enforcement.
 *
 * <p>Spring collects all implementations into the ordered list used by
 * {@link CredentialFormatDetector}; the verification strategy then dispatches polymorphically
 * by looking up the processor whose {@link #getFormat()} matches the detected format.
 *
 * <p>Adding a new credential family means adding one {@code @Component @Order(N)} class
 * that implements this interface — no edits to the strategy or the detector.
 */
public interface CredentialFormatProcessor {

  /**
   * The credential format produced by this processor. Used by the verification strategy to
   * map a detection result back to the processor that should handle it.
   */
  CredentialFormat getFormat();

  /**
   * Attempts to identify the credential as belonging to this processor's format.
   *
   * @return {@code Optional.of(getFormat())} on a positive match, {@code Optional.of(UNKNOWN)}
   * when this processor recognises the credential as belonging to its family but
   * the payload is malformed (stops evaluation), or {@code Optional.empty()} to
   * pass to the next processor in the chain
   */
  Optional<CredentialFormat> match(DetectionContext ctx);

  /**
   * Returns {@code true} when, for the given raw body, this format means the credential is
   * a compact JWT that needs signature verification. May depend on the body (some formats
   * accept both JSON-LD and JWT representations).
   */
  boolean producesJwt(String body);

  /**
   * Converts the incoming payload (JSON-LD or compact JWT) into JSON-LD ready for the
   * generic VP/VC pipeline. Implementations must handle whichever input shapes their
   * format accepts.
   */
  ContentAccessor unwrap(ContentAccessor payload);

  /**
   * Applies any format-specific policies (e.g. DID-method restriction, trust-anchor chain
   * validation). Called after JWT signature verification, only when the credential was
   * routed to this processor. Default is no-op.
   *
   * @param compactJwt   the original compact JWT body
   * @param jwtValidator validator produced by JWT signature verification, or {@code null}
   *                     when signature verification was skipped
   */
  default void enforcePolicies(String compactJwt, Validator jwtValidator) {
    // no-op by default
  }

  /**
   * Returns {@code true} if the given JSON-LD {@code @context} node contains {@code value}.
   * Handles both single-string and array forms of the context.
   */
  static boolean contextContains(JsonNode node, String value) {
    //noinspection SwitchStatementWithTooFewBranches
    return switch (node) {
      case ArrayNode array -> StreamSupport.stream(array.spliterator(), false)
          .anyMatch(n -> value.equals(n.asText()));
      default -> value.equals(node.asText());
    };
  }
}
