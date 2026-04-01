package eu.xfsc.fc.server.service;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.AdminHealthStatus;
import eu.xfsc.fc.api.generated.model.AdminHealthStatusGraphDbStatus;
import eu.xfsc.fc.api.generated.model.AdminStats;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.KeycloakAdminUrl;
import eu.xfsc.fc.core.dao.ParticipantDao;
import eu.xfsc.fc.core.dao.TrustFrameworkDao;
import eu.xfsc.fc.core.dao.UserDao;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.server.config.AdminDashboardConfig;
import eu.xfsc.fc.server.generated.controller.AdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate implementation for Admin Dashboard API endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService implements AdminApiDelegate {

  private static final Duration KEYCLOAK_TIMEOUT = Duration.ofSeconds(5);

  private final AssetStore assetStore;
  private final SchemaStore schemaStore;
  private final GraphStore graphStore;
  private final ParticipantDao participantDao;
  private final TrustFrameworkDao trustFrameworkDao;
  private final UserDao userDao;
  private final DataSource dataSource;
  private final AdminDashboardConfig config;

  @Override
  public ResponseEntity<Void> checkAdminAccess() {
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<AdminStats> getAdminStats() {
    AdminStats stats = new AdminStats();

    try {
      AssetFilter allFilter = new AssetFilter();
      allFilter.setLimit(0);
      allFilter.setOffset(0);
      long totalAssets = assetStore.getByFilter(allFilter, false, false).getTotalCount();
      stats.setTotalAssets(totalAssets);
    } catch (Exception e) {
      log.warn("Failed to get total asset count", e);
      stats.setTotalAssets(-1L);
    }

    try {
      AssetFilter activeFilter = new AssetFilter();
      activeFilter.setStatuses(List.of(AssetStatus.ACTIVE));
      activeFilter.setLimit(0);
      activeFilter.setOffset(0);
      long activeAssets = assetStore.getByFilter(activeFilter, false, false).getTotalCount();
      stats.setActiveAssets(activeAssets);
    } catch (Exception e) {
      log.warn("Failed to get active asset count", e);
      stats.setActiveAssets(-1L);
    }

    try {
      Map<SchemaStore.SchemaType, List<String>> schemas = schemaStore.getSchemaList();
      long total = schemas.values().stream().mapToInt(List::size).sum();
      stats.setTotalSchemas(total);
    } catch (Exception e) {
      log.warn("Failed to get schema count", e);
      stats.setTotalSchemas(-1L);
    }

    try {
      long totalParticipants = participantDao.search(0, 0).getTotalCount();
      stats.setTotalParticipants(totalParticipants);
    } catch (Exception e) {
      log.warn("Failed to get participant count", e);
      stats.setTotalParticipants(-1L);
    }

    try {
      long totalUsers = userDao.search(null, 0, 0).getTotalCount();
      stats.setTotalUsers(totalUsers);
    } catch (Exception e) {
      log.warn("Failed to get user count", e);
      stats.setTotalUsers(-1L);
    }

    try {
      stats.setActiveTrustFrameworks(trustFrameworkDao.countEnabled());
    } catch (Exception e) {
      log.warn("Failed to get trust framework count", e);
      stats.setActiveTrustFrameworks(-1L);
    }

    try {
      stats.setGraphClaimCount(graphStore.getClaimCount());
      stats.setGraphBackend(graphStore.getBackendType().name());
    } catch (Exception e) {
      log.warn("Failed to get graph info", e);
      stats.setGraphClaimCount(-1L);
      stats.setGraphBackend("UNKNOWN");
    }

    return ResponseEntity.ok(stats);
  }

  @Override
  public ResponseEntity<AdminHealthStatus> getAdminHealth() {
    AdminHealthStatus health = new AdminHealthStatus();

    health.setCatalogueStatus("UP");

    try {
      GraphBackendType backendType = graphStore.getBackendType();
      boolean connected = backendType != GraphBackendType.NONE && graphStore.isHealthy();
      AdminHealthStatusGraphDbStatus graphStatus = new AdminHealthStatusGraphDbStatus();
      graphStatus.setConnected(connected);
      graphStatus.setBackend(backendType.name());
      graphStatus.setClaimCount(graphStore.getClaimCount());
      health.setGraphDbStatus(graphStatus);
    } catch (Exception e) {
      log.warn("Failed to check graph DB health", e);
      AdminHealthStatusGraphDbStatus graphStatus = new AdminHealthStatusGraphDbStatus();
      graphStatus.setConnected(false);
      graphStatus.setBackend("UNKNOWN");
      graphStatus.setClaimCount(-1L);
      health.setGraphDbStatus(graphStatus);
    }

    health.setKeycloakUrl(config.getKeycloakIssuerUrl());
    try {
      config.getWebClient().get().uri(config.getKeycloakIssuerUrl()).retrieve()
          .toBodilessEntity().timeout(KEYCLOAK_TIMEOUT).block();
      health.setKeycloakStatus("UP");
    } catch (Exception e) {
      log.warn("Keycloak health check failed", e);
      health.setKeycloakStatus("DOWN");
    }

    health.setFileStorePath(config.getFileStorePath());
    try {
      File fileDir = new File(config.getFileStorePath());
      health.setFileStoreStatus(fileDir.exists() && fileDir.canWrite() ? "UP" : "DOWN");
    } catch (Exception e) {
      log.warn("File store health check failed", e);
      health.setFileStoreStatus("DOWN");
    }

    try (var conn = dataSource.getConnection()) {
      health.setDatabaseStatus(conn.isValid(5) ? "UP" : "DOWN");
    } catch (Exception e) {
      log.warn("Database health check failed", e);
      health.setDatabaseStatus("DOWN");
    }

    return ResponseEntity.ok(health);
  }

  @Override
  public ResponseEntity<KeycloakAdminUrl> getKeycloakAdminUrl() {
    KeycloakAdminUrl result = new KeycloakAdminUrl();
    result.setUrl(config.getKeycloakAdminConsoleUrl());
    return ResponseEntity.ok(result);
  }
}
