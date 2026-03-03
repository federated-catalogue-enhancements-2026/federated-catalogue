package eu.xfsc.fc.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.GraphStatus;
import eu.xfsc.fc.api.generated.model.RebuildStatus;
import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.server.generated.controller.GraphAdminApiDelegate;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate implementation for Graph Admin API endpoints.
 */
@Slf4j
@Service
public class GraphAdminService implements GraphAdminApiDelegate {

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

  @Override
  public ResponseEntity<RebuildStatus> triggerGraphRebuild() {
    log.info("triggerGraphRebuild.enter");
    boolean started = graphRebuildService.triggerRebuild(
        DEFAULT_CHUNK_COUNT, DEFAULT_CHUNK_ID, DEFAULT_THREADS, DEFAULT_BATCH_SIZE);
    RebuildStatus dto = toRebuildStatusDto(graphRebuildService.getStatus());
    HttpStatus status = started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(dto);
  }

  @Override
  public ResponseEntity<RebuildStatus> getGraphRebuildStatus() {
    RebuildStatus dto = toRebuildStatusDto(graphRebuildService.getStatus());
    return ResponseEntity.ok(dto);
  }

  @Override
  public ResponseEntity<GraphStatus> getGraphStatus() {
    GraphBackendType backendType = graphStore.getBackendType();
    boolean enabled = backendType != GraphBackendType.NONE;

    GraphStatus dto = new GraphStatus();
    dto.setBackend(backendType.name());
    dto.setEnabled(enabled);

    if (!enabled) {
      dto.setHealthy(false);
      dto.setActiveSdCount(0L);
      dto.setClaimCountInGraph(0L);
      dto.setSyncAssessment("disabled");
      return ResponseEntity.ok(dto);
    }

    dto.setHealthy(graphStore.isHealthy());

    SdFilter filter = new SdFilter();
    filter.setStatuses(List.of(SelfDescriptionStatus.ACTIVE));
    filter.setLimit(0);
    filter.setOffset(0);
    long activeSdCount = sdStore.getByFilter(filter, false, false).getTotalCount();
    long claimCount = graphStore.getClaimCount();

    dto.setActiveSdCount(activeSdCount);
    dto.setClaimCountInGraph(claimCount);

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
    dto.setSyncAssessment(syncAssessment);

    return ResponseEntity.ok(dto);
  }

  private RebuildStatus toRebuildStatusDto(GraphRebuildService.RebuildStatus internal) {
    RebuildStatus dto = new RebuildStatus();
    dto.setTotal(internal.getTotal());
    dto.setProcessed(internal.getProcessed().get());
    dto.setPercentComplete(internal.getPercentComplete());
    dto.setRunning(graphRebuildService.isRunning());
    dto.setComplete(internal.isComplete());
    dto.setFailed(internal.isFailed());
    dto.setErrorMessage(internal.getErrorMessage());
    dto.setDurationMs(internal.getDurationMs());
    return dto;
  }
}