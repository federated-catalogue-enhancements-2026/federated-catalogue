package eu.xfsc.fc.core.service.verification;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;

/**
 * Abstract base class for {@link VerificationStrategy} implementations.
 *
 * <p>Enforces protected namespace filtering via the template method pattern.
 * The {@link #extractClaims} method is final and applies {@link ProtectedNamespaceFilter}
 * automatically after calling {@link #doExtractClaims}. Concrete strategies must implement
 * {@link #doExtractClaims} instead of {@link #extractClaims}.</p>
 *
 * <p>This design ensures that namespace filtering cannot be accidentally bypassed by any
 * current or future strategy implementation.</p>
 */
public abstract class AbstractVerificationStrategy implements VerificationStrategy {

  @Autowired
  private ProtectedNamespaceFilter protectedNamespaceFilter;

  /**
   * Extracts claims from the payload and removes any triples using the protected
   * {@code fcmeta:} namespace. This method is final — override {@link #doExtractClaims} instead.
   *
   * @param payload the Self-Description content to extract claims from
   * @return filtered list of claims with protected namespace triples removed
   */
  @Override
  public final List<SdClaim> extractClaims(ContentAccessor payload) {
    List<SdClaim> claims = doExtractClaims(payload);
    return protectedNamespaceFilter.filterClaims(claims, "claims extraction");
  }

  /**
   * Performs raw claim extraction without namespace filtering. Called by {@link #extractClaims}.
   * Implementations must not apply namespace filtering here — it is handled by the base class.
   *
   * <p>Concrete subclasses must override this method with {@code @Override protected}
   * visibility and must not re-declare it as {@code public} — doing so would allow
   * callers to invoke raw extraction directly and bypass the namespace filter in
   * {@link #extractClaims}.</p>
   *
   * @param payload the Self-Description content to extract claims from
   * @return raw list of extracted claims before namespace filtering
   */
  protected abstract List<SdClaim> doExtractClaims(ContentAccessor payload);

}
