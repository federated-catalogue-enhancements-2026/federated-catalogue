package eu.xfsc.fc.server.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class GraphStoreStartupCheckerTest {

  @Autowired
  private GraphRebuildService graphRebuildService;

  @Autowired
  private GraphStoreStartupChecker startupChecker;

  @Test
  public void startupCheckerShouldNotTriggerRebuildOnEmptyDatabase() {
    // With no active SDs in the database and an empty graph, no rebuild should be running
    assertFalse(graphRebuildService.isRunning());
  }
}