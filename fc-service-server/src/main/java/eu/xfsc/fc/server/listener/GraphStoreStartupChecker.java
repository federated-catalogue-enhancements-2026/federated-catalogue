package eu.xfsc.fc.server.listener;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks the graph store state at application startup.
 * Detects empty graphs when active SDs exist and optionally triggers auto-rebuild.
 */
@Slf4j
@Component
public class GraphStoreStartupChecker implements ApplicationListener<ApplicationReadyEvent> {

  private static final int DEFAULT_REBUILD_THREADS = 4;
  private static final int DEFAULT_REBUILD_BATCH_SIZE = 100;

  @Autowired
  private GraphStore graphStore;

  @Autowired
  private SelfDescriptionStore sdStore;

  @Autowired
  private GraphRebuildService graphRebuildService;

  @Value("${graphstore.auto-rebuild-on-empty:false}")
  private boolean autoRebuildOnEmpty;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    GraphBackendType backendType = graphStore.getBackendType();
    if (backendType == GraphBackendType.NONE) {
      log.info("Graph store backend is disabled (NONE), skipping startup check");
      return;
    }

    log.info("Checking graph store state for backend: {}", backendType);

    long claimCount = graphStore.getClaimCount();
    if (claimCount == -1) {
      log.warn("Graph store connectivity check failed, cannot determine graph state");
      return;
    }

    SdFilter filter = new SdFilter();
    filter.setStatuses(List.of(SelfDescriptionStatus.ACTIVE));
    filter.setLimit(0);
    filter.setOffset(0);
    long activeSdCount = sdStore.getByFilter(filter, false, false).getTotalCount();

    if (claimCount == 0 && activeSdCount > 0) {
      log.warn("Graph store is empty but {} active SDs exist in storage", activeSdCount);
      if (autoRebuildOnEmpty) {
        log.info("Auto-rebuild is enabled, triggering graph rebuild");
        graphRebuildService.triggerRebuild(1, 0, DEFAULT_REBUILD_THREADS, DEFAULT_REBUILD_BATCH_SIZE);
      }
    } else {
      log.info("Graph store state: {} claims, {} active SDs", claimCount, activeSdCount);
    }
  }
}