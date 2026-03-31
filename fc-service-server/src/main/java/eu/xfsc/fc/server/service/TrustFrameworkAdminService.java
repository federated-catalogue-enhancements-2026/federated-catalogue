package eu.xfsc.fc.server.service;

import java.time.Duration;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.TrustFrameworkConfigUpdate;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.core.dao.TrustFrameworkDao;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.server.generated.controller.TrustFrameworkAdminApiDelegate;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for trust framework administration endpoints.
 */
@Slf4j
@Service
public class TrustFrameworkAdminService implements TrustFrameworkAdminApiDelegate {

  private static final Duration CONNECTIVITY_TIMEOUT = Duration.ofSeconds(5);

  private final TrustFrameworkDao trustFrameworkDao;
  private final WebClient webClient;

  public TrustFrameworkAdminService(TrustFrameworkDao trustFrameworkDao,
                                    WebClient.Builder webClientBuilder) {
    this.trustFrameworkDao = trustFrameworkDao;
    this.webClient = webClientBuilder.build();
  }

  @Override
  public ResponseEntity<List<TrustFrameworkEntry>> getTrustFrameworks() {
    List<TrustFrameworkConfig> frameworks = trustFrameworkDao.findAll();
    List<TrustFrameworkEntry> entries = frameworks.stream()
        .map(this::toEntry)
        .toList();
    return ResponseEntity.ok(entries);
  }

  @Override
  public ResponseEntity<Void> setTrustFrameworkEnabled(String id, Boolean enabled) {
    int updated = trustFrameworkDao.updateEnabled(id, enabled);
    if (updated == 0) {
      throw new NotFoundException("Trust framework not found: " + id);
    }
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> updateTrustFrameworkConfig(String id,
      TrustFrameworkConfigUpdate config) {
    int updated = trustFrameworkDao.updateConfig(
        id,
        config.getServiceUrl(),
        config.getApiVersion(),
        config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 30);
    if (updated == 0) {
      throw new NotFoundException("Trust framework not found: " + id);
    }
    return ResponseEntity.ok().build();
  }

  private TrustFrameworkEntry toEntry(TrustFrameworkConfig config) {
    TrustFrameworkEntry entry = new TrustFrameworkEntry();
    entry.setId(config.getId());
    entry.setName(config.getName());
    entry.setServiceUrl(config.getServiceUrl());
    entry.setApiVersion(config.getApiVersion());
    entry.setTimeoutSeconds(config.getTimeoutSeconds());
    entry.setEnabled(config.isEnabled());
    entry.setConnected(checkConnectivity(config));
    return entry;
  }

  private boolean checkConnectivity(TrustFrameworkConfig config) {
    if (config.getServiceUrl() == null || config.getServiceUrl().isBlank()) {
      return false;
    }
    try {
      webClient.get().uri(config.getServiceUrl())
          .retrieve().toBodilessEntity()
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds() > 0
              ? Math.min(config.getTimeoutSeconds(), CONNECTIVITY_TIMEOUT.getSeconds())
              : CONNECTIVITY_TIMEOUT.getSeconds()))
          .block();
      return true;
    } catch (Exception e) {
      log.warn("Trust framework connectivity check failed for {}", config.getId(), e);
      return false;
    }
  }
}
