package eu.xfsc.fc.server.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Spring Boot health indicator for the graph store backend.
 * Reports the backend type and its health status.
 */
@Component
public class GraphStoreHealthIndicator implements HealthIndicator {

  @Autowired
  private GraphStore graphStore;

  @Override
  public Health health() {
    GraphBackendType backendType = graphStore.getBackendType();
    if (backendType == GraphBackendType.NONE) {
      return Health.up()
          .withDetail("backend", backendType.name())
          .withDetail("status", "disabled")
          .build();
    }
    if (!graphStore.isHealthy()) {
      return Health.down()
          .withDetail("backend", backendType.name())
          .withDetail("reason", "Backend connectivity check failed")
          .build();
    }
    QueryLanguage queryLanguage = graphStore.getSupportedQueryLanguage();
    return Health.up()
        .withDetail("backend", backendType.name())
        .withDetail("queryLanguage", queryLanguage != null ? queryLanguage.name() : "unknown")
        .build();
  }
}