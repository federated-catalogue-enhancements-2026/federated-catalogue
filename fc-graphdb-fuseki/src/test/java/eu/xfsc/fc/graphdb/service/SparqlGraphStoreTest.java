package eu.xfsc.fc.graphdb.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.graphdb.config.EmbeddedFusekiConfig;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SparqlGraphStore.class})
@Import(EmbeddedFusekiConfig.class)
public class SparqlGraphStoreTest {

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private RDFConnection rdfConnection;

    @BeforeEach
    void clearDataset() {
        Txn.executeWrite(rdfConnection, () -> rdfConnection.update("CLEAR ALL"));
    }

    @Test
    void graphStoreBeanIsSparqlImplementation() {
        assertInstanceOf(SparqlGraphStore.class, graphStore);
    }

    @Test
    void testAddClaimsAndQuerySparqlStar() {
        List<SdClaim> claims = Arrays.asList(
            new SdClaim(
                "<http://example.org/subject1>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/ServiceOffering>"
            ),
            new SdClaim(
                "<http://example.org/subject1>",
                "<http://example.org/name>",
                "\"Test Service\""
            )
        );

        String credentialSubject = "http://example.org/credential1";
        graphStore.addClaims(claims, credentialSubject);

        // Query using SPARQL-star syntax because addClaims stores claims as triple nodes
        String sparqlStarQuery = "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <https://www.w3.org/2018/credentials#credentialSubject> ?cs }";
        GraphQuery query = new GraphQuery(sparqlStarQuery, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);

        PaginatedResults<Map<String, Object>> results = graphStore.queryData(query);

        assertNotNull(results);
        assertFalse(results.getResults().isEmpty(), "SPARQL-star query should return non-empty results");
        assertEquals(2, results.getResults().size(), "Should return 2 results for 2 claims");

        // Verify that result values are plain Java types
        Map<String, Object> firstResult = results.getResults().get(0);
        assertNotNull(firstResult.get("s"));
        assertNotNull(firstResult.get("p"));
        assertNotNull(firstResult.get("o"));
    }

    @Test
    void testAddClaimsCreatesRdfStarStatements() {
        List<SdClaim> claims = Arrays.asList(
            new SdClaim(
                "<http://example.org/subject2>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/Resource>"
            ),
            new SdClaim(
                "<http://example.org/subject2>",
                "<http://example.org/label>",
                "\"My Resource\""
            )
        );

        String credentialSubject = "http://example.org/credential2";
        graphStore.addClaims(claims, credentialSubject);

        // Query for the RDF-star meta-properties
        String sparqlStarQuery = "SELECT ?s ?p ?o ?mp ?mo WHERE { <<?s ?p ?o>> ?mp ?mo }";
        GraphQuery query = new GraphQuery(sparqlStarQuery, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);

        PaginatedResults<Map<String, Object>> results = graphStore.queryData(query);

        assertFalse(results.getResults().isEmpty(), "Should have RDF-star statements");
        assertEquals(2, results.getResults().size(), "Should have 2 RDF-star wrapped statements");

        for (Map<String, Object> row : results.getResults()) {
            // Each claim should be wrapped with cred:credentialSubject pointing to our subject
            assertEquals("https://www.w3.org/2018/credentials#credentialSubject", row.get("mp"),
                "Meta-property should be credentialSubject URI");
            assertEquals(credentialSubject, row.get("mo"),
                "Meta-object should match the credential subject passed to addClaims");
        }
    }

    @Test
    void testDeleteClaimsRemovesTargetOnly() {
        // Add claims for credential subject 1
        List<SdClaim> claims1 = Arrays.asList(
            new SdClaim(
                "<http://example.org/subjectA>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/TypeA>"
            ),
            new SdClaim(
                "<http://example.org/subjectA>",
                "<http://example.org/name>",
                "\"Subject A\""
            )
        );
        String credSub1 = "http://example.org/credentialA";
        graphStore.addClaims(claims1, credSub1);

        // Add claims for credential subject 2
        List<SdClaim> claims2 = Arrays.asList(
            new SdClaim(
                "<http://example.org/subjectB>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/TypeB>"
            ),
            new SdClaim(
                "<http://example.org/subjectB>",
                "<http://example.org/name>",
                "\"Subject B\""
            )
        );
        String credSub2 = "http://example.org/credentialB";
        graphStore.addClaims(claims2, credSub2);

        // Delete only credential subject 1
        graphStore.deleteClaims(credSub1);

        // Verify: 0 results for deleted subject
        String queryDeleted = "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <https://www.w3.org/2018/credentials#credentialSubject> <" + credSub1 + "> }";
        GraphQuery gqDeleted = new GraphQuery(queryDeleted, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);
        PaginatedResults<Map<String, Object>> deletedResults = graphStore.queryData(gqDeleted);
        assertTrue(deletedResults.getResults().isEmpty(), "Deleted credential subject should have 0 results");

        // Verify: non-empty results for surviving subject
        String querySurviving = "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <https://www.w3.org/2018/credentials#credentialSubject> <" + credSub2 + "> }";
        GraphQuery gqSurviving = new GraphQuery(querySurviving, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);
        PaginatedResults<Map<String, Object>> survivingResults = graphStore.queryData(gqSurviving);
        assertFalse(survivingResults.getResults().isEmpty(), "Surviving credential subject should still have results");
        assertEquals(2, survivingResults.getResults().size(), "Surviving subject should have 2 claims");
    }

    @Test
    void testQueryDataPreservesOrderByClause() {
        // Add multiple claims that will produce an ordered result
        List<SdClaim> claims = Arrays.asList(
            new SdClaim(
                "<http://example.org/item1>",
                "<http://example.org/name>",
                "\"Charlie\""
            ),
            new SdClaim(
                "<http://example.org/item2>",
                "<http://example.org/name>",
                "\"Alice\""
            ),
            new SdClaim(
                "<http://example.org/item3>",
                "<http://example.org/name>",
                "\"Bob\""
            )
        );
        String credentialSubject = "http://example.org/credentialOrder";
        graphStore.addClaims(claims, credentialSubject);

        // Query with ORDER BY to verify ordering is preserved (not shuffled)
        String sparqlQuery = "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <https://www.w3.org/2018/credentials#credentialSubject> ?cs } ORDER BY ?o";
        GraphQuery query = new GraphQuery(sparqlQuery, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);

        PaginatedResults<Map<String, Object>> results = graphStore.queryData(query);
        assertEquals(3, results.getResults().size(), "Should return 3 results");

        // With ORDER BY ?o, results should be sorted: Alice, Bob, Charlie
        assertEquals("Alice", results.getResults().get(0).get("o"));
        assertEquals("Bob", results.getResults().get(1).get("o"));
        assertEquals("Charlie", results.getResults().get(2).get("o"));
    }

    @Test
    void testAddClaimsValidation() {
        String credentialSubject = "http://example.org/credentialValidation";

        // Syntactically correct claim should pass
        SdClaim validClaim = new SdClaim(
            "<http://example.org/subject>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://example.org/Type>"
        );
        assertDoesNotThrow(
            () -> graphStore.addClaims(Collections.singletonList(validClaim), credentialSubject),
            "A syntactically correct triple should pass validation"
        );

        // Broken subject URI should be rejected
        SdClaim brokenSubject = new SdClaim(
            "<__http://example.org/broken__>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://example.org/Type>"
        );
        Exception exception = assertThrows(
            QueryException.class,
            () -> graphStore.addClaims(Collections.singletonList(brokenSubject), credentialSubject),
            "A broken subject URI should be rejected"
        );
        assertTrue(exception.getMessage().contains("Subject in triple"),
            "Error should mention the subject");

        // Broken predicate URI should be rejected
        SdClaim brokenPredicate = new SdClaim(
            "<http://example.org/subject>",
            "<__http://example.org/broken__>",
            "<http://example.org/Type>"
        );
        exception = assertThrows(
            QueryException.class,
            () -> graphStore.addClaims(Collections.singletonList(brokenPredicate), credentialSubject),
            "A broken predicate URI should be rejected"
        );
        assertTrue(exception.getMessage().contains("Predicate in triple"),
            "Error should mention the predicate");

        // Broken object URI should be rejected
        SdClaim brokenObject = new SdClaim(
            "<http://example.org/subject>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<__http://example.org/broken__>"
        );
        exception = assertThrows(
            QueryException.class,
            () -> graphStore.addClaims(Collections.singletonList(brokenObject), credentialSubject),
            "A broken object URI should be rejected"
        );
        assertTrue(exception.getMessage().contains("Object in triple"),
            "Error should mention the object");
    }

    @ParameterizedTest
    @EnumSource(value = QueryLanguage.class, names = {"OPENCYPHER", "GRAPHQL"})
    void testQueryDataRejectsNonSparql(QueryLanguage language) {
        GraphQuery query = new GraphQuery("SELECT * WHERE { ?s ?p ?o }", Map.of(),
            language, GraphQuery.QUERY_TIMEOUT, false);

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> graphStore.queryData(query),
            "Should reject " + language + " query language"
        );
        assertTrue(exception.getMessage().contains(language.name()),
            "Exception message should contain the rejected language name: " + language.name());
    }

    @Test
    void isHealthyShouldReturnTrue() {
        assertTrue(graphStore.isHealthy(),
            "isHealthy() should return true for embedded Fuseki");
    }

    @Test
    void getClaimCountShouldReturnZeroWhenEmpty() {
        long count = graphStore.getClaimCount();
        assertEquals(0, count,
            "getClaimCount() should return 0 on empty dataset");
    }

    @Test
    void getClaimCountShouldReturnCountAfterAddClaims() {
        List<SdClaim> claims = Arrays.asList(
            new SdClaim(
                "<http://example.org/healthSubject>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/ServiceOffering>"
            ),
            new SdClaim(
                "<http://example.org/healthSubject>",
                "<http://example.org/name>",
                "\"Health Check Service\""
            )
        );
        String credentialSubject = "http://example.org/healthCredential";
        graphStore.addClaims(claims, credentialSubject);

        long count = graphStore.getClaimCount();
        assertEquals(2, count,
            "getClaimCount() should return 2 for 2 claim triples");
    }

    @Test
    void testAddClaimsEmptyList() {
        String credentialSubject = "http://example.org/emptySubject";
        assertDoesNotThrow(
            () -> graphStore.addClaims(Collections.emptyList(), credentialSubject),
            "Adding empty claim list should be a safe no-op"
        );

        // Verify nothing was stored
        String sparqlQuery = "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <https://www.w3.org/2018/credentials#credentialSubject> <" + credentialSubject + "> }";
        GraphQuery query = new GraphQuery(sparqlQuery, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);
        PaginatedResults<Map<String, Object>> results = graphStore.queryData(query);
        assertTrue(results.getResults().isEmpty(), "No results should be stored for empty claim list");
    }
}