package eu.xfsc.fc.core.service.graphdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService.RebuildStatus;

/**
 * Tests for {@link GraphRebuildService} and its inner {@link RebuildStatus} class.
 * Tests that do not require a full Spring context.
 */
public class GraphRebuildServiceTest {

  @Test
  public void triggerRebuildOnDisabledStoreShouldThrow() {
    DummyGraphStore dummyStore = new DummyGraphStore();
    GraphRebuildService service = new GraphRebuildService(null, null, dummyStore);
    assertThrows(GraphStoreDisabledException.class,
        () -> service.triggerRebuild(1, 0, 4, 100));
  }

  @Test
  public void rebuildStatusIdleShouldBeComplete() {
    RebuildStatus idle = RebuildStatus.idle();
    assertTrue(idle.isComplete());
    assertEquals(0, idle.getTotal());
    assertEquals(100, idle.getPercentComplete());
  }

  @Test
  public void rebuildStatusShouldTrackProgress() {
    RebuildStatus status = new RebuildStatus(10);
    assertEquals(10, status.getTotal());
    assertEquals(0, status.getProcessed().get());
    assertEquals(0, status.getPercentComplete());
    assertFalse(status.isComplete());

    status.incrementProcessed();
    status.incrementProcessed();
    status.incrementProcessed();
    assertEquals(3, status.getProcessed().get());
    assertEquals(30, status.getPercentComplete());

    status.markComplete();
    assertTrue(status.isComplete());
    assertFalse(status.isFailed());
  }

  @Test
  public void rebuildStatusMarkFailedShouldSetError() {
    RebuildStatus status = new RebuildStatus(5);
    status.markFailed("test error");
    assertTrue(status.isFailed());
    assertTrue(status.isComplete());
    assertEquals("test error", status.getErrorMessage());
  }

  @Test
  public void rebuildStatusDurationShouldBePositive() {
    RebuildStatus status = new RebuildStatus(1);
    assertTrue(status.getDurationMs() >= 0);
  }
}