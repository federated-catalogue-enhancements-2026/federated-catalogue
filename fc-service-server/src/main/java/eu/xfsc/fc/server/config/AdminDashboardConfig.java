package eu.xfsc.fc.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.Getter;

/**
 * Configuration holder for Admin Dashboard service properties.
 * Groups Keycloak, file store, and WebClient config to reduce constructor parameters.
 */
@Getter
@Component
public class AdminDashboardConfig {

  private final WebClient webClient;
  private final String keycloakIssuerUrl;
  private final String keycloakAdminConsoleUrl;
  private final String fileStorePath;

  public AdminDashboardConfig(
      WebClient.Builder webClientBuilder,
      @Value("${keycloak.auth-server-url}") String keycloakAuthServerUrl,
      @Value("${keycloak.realm}") String keycloakRealm,
      @Value("${keycloak.admin-console-url:}") String keycloakAdminConsoleUrl,
      @Value("${datastore.file-path}") String fileStorePath) {
    this.webClient = webClientBuilder.build();
    this.keycloakIssuerUrl = keycloakAuthServerUrl + "/realms/" + keycloakRealm;
    this.keycloakAdminConsoleUrl = keycloakAdminConsoleUrl;
    this.fileStorePath = fileStorePath;
  }
}
