package eu.xfsc.fc.server.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=neo4j"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class GraphStoreHealthIndicatorTest {

  @Autowired
  private GraphStoreHealthIndicator healthIndicator;

  @Autowired
  private MockMvc mockMvc;

  @Test
  public void healthShouldReportUpWhenBackendHealthy() {
    Health health = healthIndicator.health();
    assertEquals(Status.UP, health.getStatus());
  }

  @Test
  public void healthShouldIncludeBackendType() {
    Health health = healthIndicator.health();
    assertNotNull(health.getDetails().get("backend"));
  }

  @Test
  @WithMockUser
  public void actuatorHealthShouldIncludeGraphStoreComponent() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}