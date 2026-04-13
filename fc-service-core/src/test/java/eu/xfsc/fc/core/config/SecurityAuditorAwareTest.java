package eu.xfsc.fc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityAuditorAwareTest {

  private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getCurrentAuditor_noAuthentication_returnsEmpty() {
    SecurityContextHolder.clearContext();

    Optional<String> result = auditorAware.getCurrentAuditor();

    assertTrue(result.isEmpty());
  }

  @Test
  void getCurrentAuditor_unauthenticatedAuthentication_returnsEmpty() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(false);
    SecurityContext context = mock(SecurityContext.class);
    when(context.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(context);

    Optional<String> result = auditorAware.getCurrentAuditor();

    assertTrue(result.isEmpty());
  }

  @Test
  void getCurrentAuditor_jwtPrincipal_returnsSubject() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("test-user");
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getPrincipal()).thenReturn(jwt);
    SecurityContext context = mock(SecurityContext.class);
    when(context.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(context);

    Optional<String> result = auditorAware.getCurrentAuditor();

    assertTrue(result.isPresent());
    assertEquals("test-user", result.get());
  }

  @Test
  void getCurrentAuditor_nonJwtPrincipal_returnsEmpty() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getPrincipal()).thenReturn("string-principal");
    SecurityContext context = mock(SecurityContext.class);
    when(context.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(context);

    Optional<String> result = auditorAware.getCurrentAuditor();

    assertTrue(result.isEmpty());
  }
}
