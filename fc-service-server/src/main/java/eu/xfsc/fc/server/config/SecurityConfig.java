package eu.xfsc.fc.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import eu.xfsc.fc.api.generated.model.Error;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_UPDATE;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_READ;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_UPDATE;
import static eu.xfsc.fc.server.util.CommonConstants.SD_ADMIN_ROLE;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Note: WebSecurity adapter is deprecated in spring security 5.7;
 * so we are using SecurityFilterChain for configuration security without extending deprecated adapter.
 */
@Configuration
@EnableWebSecurity //(debug = true)
//@EnableMethodSecurity
public class SecurityConfig {
  private static final ObjectMapper mapper = new ObjectMapper();

  private static final String COMMON_FORBIDDEN_ERROR_MESSAGE = "User does not have permission to execute this request.";

  @Value("${keycloak.resource}")
  private String resourceId;

  /**
   * Define security constraints for the application resources.
   */
  // TODO: 13.07.2022 Need to add access by scopes and by access to the participant.
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
      //.csrf().disable()
      .authorizeHttpRequests(authorization -> authorization
          .requestMatchers(antMatcher(HttpMethod.OPTIONS, "/**")).permitAll()
          .requestMatchers(antMatcher(HttpMethod.GET, "/api/**")).permitAll()
          .requestMatchers(antMatcher(HttpMethod.GET, "/swagger-ui/**")).permitAll()
          .requestMatchers(antMatcher(HttpMethod.GET, "/actuator")).permitAll()
          .requestMatchers(antMatcher(HttpMethod.GET, "/actuator/**")).permitAll()
          .requestMatchers(antMatcher(HttpMethod.GET, "/js/**")).permitAll()
          .requestMatchers(antMatcher(HttpMethod.GET, "/css/**")).permitAll()

          // Schema APIs
          .requestMatchers(antMatcher(HttpMethod.POST, "/schemas")).hasAnyRole(SCHEMA_CREATE, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.DELETE, "/schemas/**")).hasAnyRole(SCHEMA_DELETE, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.PUT, "/schemas")).hasAnyRole(SCHEMA_UPDATE, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.GET, "/schemas")).hasAnyRole(SCHEMA_READ, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.GET, "/schemas/**")).hasAnyRole(SCHEMA_READ, ADMIN_ALL)

          // Query APIs
          .requestMatchers(antMatcher("/query")).permitAll()
          .requestMatchers(antMatcher("/query/**")).permitAll()

          // Verification APIs
          .requestMatchers(antMatcher("/verification")).permitAll()

          // Self-Description APIs
          .requestMatchers(antMatcher(HttpMethod.GET, "/self-descriptions")).hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.GET, "/self-descriptions/{self_description_hash}")).hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.POST, "/self-descriptions"))
          		.hasAnyRole(ASSET_CREATE, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.DELETE, "/self-descriptions/{self_description_hash}"))
          		.hasAnyRole(ASSET_DELETE, ADMIN_ALL)
          .requestMatchers(antMatcher(HttpMethod.POST, "/self-descriptions/{self_description_hash}/revoke"))
          		.hasAnyRole(ASSET_UPDATE, ADMIN_ALL)

          // Participants API
          .requestMatchers(antMatcher(HttpMethod.POST, "/participants")).hasRole(CATALOGUE_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.GET, "/participants")).hasAnyRole(CATALOGUE_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.PUT, "/participants/*")).hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.DELETE, "/participants/*")).hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.GET, "/participants/*"))
            	.hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE, SD_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.GET, "/participants/*/users"))
            	.hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE)

          // User APIs
          .requestMatchers(antMatcher("/users"))
              .hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE)
          .requestMatchers(antMatcher("/users/{userId}"))
              .hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE)

          // Roles APIs
          .requestMatchers(antMatcher("/roles")).authenticated()

          // Session APIs
          .requestMatchers(antMatcher("/session")).authenticated()

          // Graph Admin APIs
          .requestMatchers(antMatcher(HttpMethod.POST, "/admin/graph/rebuild")).hasAnyRole(ADMIN_ALL, CATALOGUE_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.GET, "/admin/graph/rebuild/status")).hasAnyRole(ADMIN_ALL, CATALOGUE_ADMIN_ROLE)
          .requestMatchers(antMatcher(HttpMethod.GET, "/admin/graph/status")).hasAnyRole(ADMIN_ALL, CATALOGUE_ADMIN_ROLE)

          // Actuator graph-rebuild (requires admin role, matching /admin/graph/rebuild)
          .requestMatchers(antMatcher(HttpMethod.POST, "/actuator/graph-rebuild")).hasAnyRole(ADMIN_ALL, CATALOGUE_ADMIN_ROLE)

          .anyRequest().authenticated()
        )
        .exceptionHandling(c -> c.accessDeniedHandler(accessDeniedHandler()))
        .oauth2ResourceServer(c -> c.jwt(jc -> jc.jwtAuthenticationConverter(new CustomJwtAuthenticationConverter(resourceId))));

    return http.build();
  }

  /**
   * Customize Access Denied application exception.
   */
  private static AccessDeniedHandler accessDeniedHandler() {
    return (HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) -> {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      Error forbiddenError =
          new Error("forbidden_error", accessDeniedException.getMessage().contains("Access is denied")
              ? accessDeniedException.getMessage() : COMMON_FORBIDDEN_ERROR_MESSAGE);
      ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
      response.getWriter().write(ow.writeValueAsString(forbiddenError));
    };
  }
}
