package eu.xfsc.fc.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for protected RDF namespace filtering.
 *
 * <p>The {@code namespace} value is the single source of truth for which namespace URI
 * is filtered at ingestion boundaries. Configured via
 * {@code federated-catalogue.verification.protected-namespace.namespace} in
 * {@code application.yml}.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "federated-catalogue.verification.protected-namespace", ignoreInvalidFields = true)
public class ProtectedNamespaceProperties {

  private String namespace;

  private String prefix;
}
