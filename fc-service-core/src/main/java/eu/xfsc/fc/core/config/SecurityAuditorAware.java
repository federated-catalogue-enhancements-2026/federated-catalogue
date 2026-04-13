package eu.xfsc.fc.core.config;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Provides the current JWT subject as the Spring Data JPA auditor.
 * Returns empty when there is no active security context (background jobs, tests).
 */
@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<String> {

  /**
   * Returns the JWT subject of the currently authenticated principal, or empty if there is
   * no active authentication or the principal is not a JWT (e.g., background jobs, tests).
   */
  @Override
  public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Optional.empty();
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof Jwt jwt) {
      return Optional.ofNullable(jwt.getSubject());
    }
    return Optional.empty();
  }
}
