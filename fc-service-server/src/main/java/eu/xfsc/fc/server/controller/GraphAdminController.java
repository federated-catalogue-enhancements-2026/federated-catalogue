package eu.xfsc.fc.server.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService.RebuildStatus;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for graph database administration operations.
 * Authorization is enforced via SecurityFilterChain URL matchers in SecurityConfig.
 */
@Slf4j
@RestController
@RequestMapping("/admin/graph")
public class GraphAdminController {

  private static final int DEFAULT_CHUNK_COUNT = 1;
  private static final int DEFAULT_CHUNK_ID = 0;
  private static final int DEFAULT_THREADS = 4;
  private static final int DEFAULT_BATCH_SIZE = 100;

  @Autowired
  private GraphRebuildService graphRebuildService;

  @Autowired
  private GraphStore graphStore;

  @Autowired
  private SelfDescriptionStore sdStore;

  /**
   * Triggers an asynchronous graph rebuild.
   *
   * @return 202 Accepted if rebuild started, 409 Conflict if already running
   */
  @PostMapping("/rebuild")
  public ResponseEntity<Map<String, Object>> triggerRebuild() {
    log.info("triggerRebuild.enter");
    boolean started = graphRebuildService.triggerRebuild(
        DEFAULT_CHUNK_COUNT, DEFAULT_CHUNK_ID, DEFAULT_THREADS, DEFAULT_BATCH_SIZE);
    String message = started ? "Graph rebuild started" : "Graph rebuild already in progress";
    HttpStatus status = started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(Map.of("message", message));
  }

  /**
   * Returns the current rebuild status.
   *
   * @return 200 OK with rebuild status details
   */
  @GetMapping("/rebuild/status")
  public ResponseEntity<Map<String, Object>> getRebuildStatus() {
    RebuildStatus rebuildStatus = graphRebuildService.getStatus();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("total", rebuildStatus.getTotal());
    body.put("processed", rebuildStatus.getProcessed().get());
    body.put("percentComplete", rebuildStatus.getPercentComplete());
    body.put("running", graphRebuildService.isRunning());
    body.put("complete", rebuildStatus.isComplete());
    body.put("failed", rebuildStatus.isFailed());
    body.put("errorMessage", rebuildStatus.getErrorMessage());
    body.put("durationMs", rebuildStatus.getDurationMs());
    return ResponseEntity.ok(body);
  }

  /**
   * Returns the current graph store status including backend type, health, and sync assessment.
   *
   * @return 200 OK with graph status details
   */
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getGraphStatus() {
    GraphBackendType backendType = graphStore.getBackendType();
    boolean enabled = backendType != GraphBackendType.NONE;

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("backend", backendType.name());
    body.put("enabled", enabled);

    if (!enabled) {
      body.put("healthy", false);
      body.put("activeSdCount", 0);
      body.put("claimCountInGraph", 0);
      body.put("syncAssessment", "disabled");
      return ResponseEntity.ok(body);
    }

    boolean healthy = graphStore.isHealthy();
    body.put("healthy", healthy);

    SdFilter filter = new SdFilter();
    filter.setStatuses(List.of(SelfDescriptionStatus.ACTIVE));
    filter.setLimit(0);
    filter.setOffset(0);
    long activeSdCount = sdStore.getByFilter(filter, false, false).getTotalCount();
    long claimCount = graphStore.getClaimCount();

    body.put("activeSdCount", activeSdCount);
    body.put("claimCountInGraph", claimCount);

    String syncAssessment;
    if (claimCount == -1) {
      syncAssessment = "unknown";
    } else if (claimCount == 0 && activeSdCount == 0) {
      syncAssessment = "empty";
    } else if (claimCount == 0 && activeSdCount > 0) {
      syncAssessment = "out-of-sync";
    } else if (claimCount > 0 && activeSdCount == 0) {
      syncAssessment = "out-of-sync";
    } else {
      syncAssessment = "in-sync";
    }
    body.put("syncAssessment", syncAssessment);

    return ResponseEntity.ok(body);
  }
}