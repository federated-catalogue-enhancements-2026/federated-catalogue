package eu.xfsc.fc.core.service.graphdb;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.util.GraphRebuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps {@link GraphRebuilder} with async execution and rebuild status tracking.
 * Guards against concurrent rebuilds via an {@link AtomicBoolean} flag.
 */
@Slf4j
@Component
public class GraphRebuildService {

  private final GraphRebuilder graphRebuilder;
  private final SelfDescriptionStore sdStore;
  private final GraphStore graphStore;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private volatile RebuildStatus status = RebuildStatus.idle();

  @Autowired
  public GraphRebuildService(GraphRebuilder graphRebuilder, SelfDescriptionStore sdStore,
                             GraphStore graphStore) {
    this.graphRebuilder = graphRebuilder;
    this.sdStore = sdStore;
    this.graphStore = graphStore;
  }

  /**
   * Triggers an async graph rebuild. Returns {@code true} if the rebuild was started,
   * {@code false} if a rebuild is already in progress.
   *
   * @param chunkCount total number of parallel rebuilders
   * @param chunkId 0-based index of this rebuilder
   * @param threads number of threads for the rebuild
   * @param batchSize number of hashes to fetch per batch
   * @return true if rebuild was started, false if already running
   * @throws GraphStoreDisabledException if the graph store backend is disabled
   */
  public boolean triggerRebuild(int chunkCount, int chunkId, int threads, int batchSize) {
    if (graphStore.getBackendType() == GraphBackendType.NONE) {
      throw new GraphStoreDisabledException("Graph store is disabled");
    }
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    SdFilter filter = new SdFilter();
    filter.setStatuses(List.of(SelfDescriptionStatus.ACTIVE));
    filter.setLimit(0);
    filter.setOffset(0);
    long total = sdStore.getByFilter(filter, false, false).getTotalCount();

    status = new RebuildStatus(total);
    executor.submit(() -> {
      try {
        graphRebuilder.rebuildGraphDb(chunkCount, chunkId, threads, batchSize,
            count -> status.incrementProcessed());
        status.markComplete();
      } catch (Exception e) {
        log.error("Graph rebuild failed", e);
        status.markFailed(e.getMessage());
      } finally {
        running.set(false);
      }
    });
    return true;
  }

  /**
   * Returns whether a rebuild is currently running.
   *
   * @return true if a rebuild is in progress
   */
  public boolean isRunning() {
    return running.get();
  }

  /**
   * Returns the current rebuild status.
   *
   * @return the current {@link RebuildStatus}
   */
  public RebuildStatus getStatus() {
    return status;
  }

  /**
   * Tracks the progress of a graph rebuild operation.
   */
  @Getter
  public static class RebuildStatus {
    private final long total;
    private final AtomicLong processed = new AtomicLong(0);
    private final long startTimeMs;
    private volatile boolean complete;
    private volatile boolean failed;
    private volatile String errorMessage;

    /**
     * Creates a new status tracker for a rebuild with the given total SD count.
     *
     * @param total the total number of SDs to process
     */
    public RebuildStatus(long total) {
      this.total = total;
      this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Creates an idle status indicating no rebuild is in progress.
     *
     * @return an idle RebuildStatus
     */
    public static RebuildStatus idle() {
      RebuildStatus idle = new RebuildStatus(0);
      idle.complete = true;
      return idle;
    }

    /**
     * Increments the processed count by one.
     */
    public void incrementProcessed() {
      processed.incrementAndGet();
    }

    /**
     * Marks the rebuild as complete.
     */
    public void markComplete() {
      this.complete = true;
    }

    /**
     * Marks the rebuild as failed with the given error message.
     *
     * @param message the error message
     */
    public void markFailed(String message) {
      this.failed = true;
      this.errorMessage = message;
      this.complete = true;
    }

    /**
     * Returns the completion percentage (0-100).
     *
     * @return the percentage complete
     */
    public int getPercentComplete() {
      if (total == 0) {
        return complete ? 100 : 0;
      }
      return (int) (processed.get() * 100 / total);
    }

    /**
     * Returns the elapsed duration in milliseconds since the rebuild started.
     *
     * @return duration in milliseconds
     */
    public long getDurationMs() {
      return System.currentTimeMillis() - startTimeMs;
    }
  }
}