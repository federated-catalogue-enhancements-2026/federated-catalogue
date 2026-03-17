package eu.xfsc.fc.core.service.verification;

import com.danubetech.verifiablecredentials.jwt.JwtVerifiableCredentialV2;
import com.danubetech.verifiablecredentials.jwt.JwtVerifiablePresentationV2;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Detects JWT-wrapped Verifiable Credentials and Presentations and unwraps them
 * to JSON-LD before the main verification pipeline processes them.
 *
 * <p>JWT detection is based on the content body: payloads starting with {@code eyJ}
 * (the base64url-encoded JWT header) are treated as compact serializations.
 * The preprocessor first attempts to parse as a VC 2.0 JWT, then as a VP 2.0 JWT.
 * Non-JWT content is returned unchanged.
 *
 * <p>Signature verification is out of scope here — see Issue #12.
 */
@Slf4j
@Component
public class JwtContentPreprocessor {

  private static final String JWT_PREFIX = "eyJ";

  /**
   * Unwraps a JWT-wrapped credential or presentation to JSON-LD.
   *
   * @param content the incoming content accessor
   * @return unwrapped JSON-LD content, or the original content if not JWT-wrapped
   */
  public ContentAccessor unwrap(ContentAccessor content) {
    String body = content.getContentAsString().strip();
    if (!body.startsWith(JWT_PREFIX)) {
      return content;
    }
    log.debug("unwrap; detected JWT-like content, attempting unwrap");

    try {
      String json = JwtVerifiableCredentialV2.fromCompactSerialization(body)
          .getPayloadObject().toJson();
      log.debug("unwrap; successfully unwrapped as VC 2.0 JWT");
      return new ContentAccessorDirect(json);
    } catch (Exception vcEx) {
      log.debug("unwrap; not a VC 2.0 JWT ({}), trying VP 2.0 JWT", vcEx.getMessage());
    }

    try {
      String json = JwtVerifiablePresentationV2.fromCompactSerialization(body)
          .getPayloadObject().toJson();
      log.debug("unwrap; successfully unwrapped as VP 2.0 JWT");
      return new ContentAccessorDirect(json);
    } catch (Exception vpEx) {
      log.debug("unwrap; not a VP 2.0 JWT either ({}), rejecting", vpEx.getMessage());
    }

    throw new ClientException("Content appears to be JWT-wrapped but could not be parsed as VC 2.0 or VP 2.0");
  }
}
