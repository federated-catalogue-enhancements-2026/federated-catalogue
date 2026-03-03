package eu.xfsc.fc.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.pojo.CatalogueNamespaces;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for protected RDF namespace filtering.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "federated-catalogue.verification.protected-namespace", ignoreInvalidFields = true)
public class ProtectedNamespaceProperties {

  private String namespace = CatalogueNamespaces.FC_META_NAMESPACE;

  private String prefix = CatalogueNamespaces.FC_META_PREFIX;
}
