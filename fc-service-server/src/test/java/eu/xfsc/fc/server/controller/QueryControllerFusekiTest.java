package eu.xfsc.fc.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.api.generated.model.Results;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST-level integration test for the /query endpoint with SPARQL (Fuseki) backend.
 * Verifies the full HTTP flow: upload RDF claims, run SPARQL query via REST, verify JSON response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
public class QueryControllerFusekiTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GraphStore graphStore;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeAll
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    // Load test data via GraphStore.addClaims()
    List<SdClaim> claims = Arrays.asList(
        new SdClaim(
            "<http://example.org/service1>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://example.org/ServiceOffering>"
        ),
        new SdClaim(
            "<http://example.org/service1>",
            "<http://example.org/name>",
            "\"Test SPARQL Service\""
        )
    );
    graphStore.addClaims(claims, "http://example.org/credential-fuseki-test");
  }

  @Test
  public void testSparqlSelectQueryReturnsResults() throws Exception {
    String sparqlStatement = "{\"statement\": \"SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> "
        + "<https://www.w3.org/2018/credentials#credentialSubject> ?cs }\", \"parameters\": null}";

    String response = mockMvc.perform(MockMvcRequestBuilders.post("/query")
            .content(sparqlStatement)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .queryParam("queryLanguage", QueryLanguage.SPARQL.getValue())
            .header("Accept", "application/json"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    Results result = objectMapper.readValue(response, Results.class);
    assertFalse(result.getItems().isEmpty(), "SPARQL SELECT query should return non-empty results");
    assertEquals(2, result.getItems().size(), "Should return 2 results for 2 claims");
  }

  @Test
  public void testSparqlWriteQueryRejected() throws Exception {
    String insertStatement = "{\"statement\": \"INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> <http://example.org/o> }\", \"parameters\": null}";

    mockMvc.perform(MockMvcRequestBuilders.post("/query")
            .content(insertStatement)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .queryParam("queryLanguage", QueryLanguage.SPARQL.getValue())
            .header("Accept", "application/json"))
        .andExpect(status().is5xxServerError());
  }

  @Test
  public void testSparqlInvalidSyntaxReturnsError() throws Exception {
    String badStatement = "{\"statement\": \"SELCT ?s WERE { ?s ?p ?o }\", \"parameters\": null}";

    mockMvc.perform(MockMvcRequestBuilders.post("/query")
            .content(badStatement)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .queryParam("queryLanguage", QueryLanguage.SPARQL.getValue())
            .header("Accept", "application/json"))
        .andExpect(status().is5xxServerError());
  }
}