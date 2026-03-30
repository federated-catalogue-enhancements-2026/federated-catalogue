package eu.xfsc.fc.core.service.verification;

/**
 * Shared string constants for the verification pipeline.
 */
public final class VerificationConstants {

  public static final String JWT_PREFIX = "eyJ";

  public static final String VC_11_CONTEXT = "https://www.w3.org/2018/credentials/v1";
  public static final String VC_20_CONTEXT = "https://www.w3.org/ns/credentials/v2";

  public static final String RDF_CONTEXT_KEY = "@context";

  private VerificationConstants() {
  }
}
