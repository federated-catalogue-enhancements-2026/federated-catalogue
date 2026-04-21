package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.QUERY_EXECUTE;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.Results;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration test for CAT-FR-GD-02: non-credential RDF claims extraction.
 *
 * <p>SRS verification: Upload an RDF/JSON-LD asset that contains one triple.
 * Expect this triple to be extracted to the graph database.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
public class NonCredentialRdfGraphExtractionTest {

    private static final String SPARQL_CONTENT_TYPE = "application/sparql-query";
    private static final String PROP_CREDENTIAL_SUBJECT = "https://www.w3.org/2018/credentials#credentialSubject";
    private static final String TEST_ISSUER = "http://example.org/test-issuer";
    private static final String TEST_ASSET_SUBJECT = "http://example.org/item1";
    private static final String TEST_ASSET_PREDICATE = "http://example.org/name";
    private static final String TEST_ASSET_OBJECT = "Cloud Storage Service";

    // Single-triple JSON-LD document — no VerifiableCredential/VerifiablePresentation wrapper.
    // FormatDetector returns UNKNOWN → non-credential extraction path is taken → JenaAllTriplesExtractor is used.
    private static final String SINGLE_TRIPLE_JSONLD = """
            {
              "@context": {"ex": "http://example.org/"},
              "@id": "ex:item1",
              "ex:name": "%s"
            }
            """.formatted(TEST_ASSET_OBJECT);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssetStore assetStore;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * SRS verification: upload a JSON-LD asset with one triple → triple is queryable via SPARQL.
     */
    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, PREFIX + QUERY_EXECUTE},
            claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
                    @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    void uploadNonCredentialJsonLd_singleTriple_isExtractedToGraph() throws Exception {
        byte[] content = SINGLE_TRIPLE_JSONLD.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "item.jsonld",
                "application/ld+json", content);

        // Arrange: verify graph is empty before upload
        String queryBefore = sparqlForAsset(TEST_ASSET_SUBJECT);
        String responseBefore = postQuery(queryBefore);
        Results resultsBefore = objectMapper.readValue(responseBefore, Results.class);
        assertEquals(0, resultsBefore.getItems().size(), "Graph must be empty before upload");

        // Act: upload the non-credential JSON-LD asset
        MvcResult uploadResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                        .file(file)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        Asset asset = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getId(), "Asset ID must be assigned");
        assertNotNull(asset.getAssetHash(), "Asset hash must be set");

        // Assert: the extracted triple is queryable via SPARQL
        String queryAfter = sparqlForAsset(asset.getId());
        String responseAfter = postQuery(queryAfter);
        Results resultsAfter = objectMapper.readValue(responseAfter, Results.class);

        assertFalse(resultsAfter.getItems().isEmpty(), "Graph must contain at least one extracted triple");

        Map<String, Object> triple = resultsAfter.getItems().get(0);
        assertEquals(TEST_ASSET_SUBJECT, triple.get("s"),
                "Subject must match the @id from the uploaded JSON-LD");
        assertEquals(TEST_ASSET_PREDICATE, triple.get("p"),
                "Predicate must match ex:name");
        assertEquals(TEST_ASSET_OBJECT, triple.get("o"),
                "Object must match the name literal");

        assetStore.deleteAsset(asset.getAssetHash());
    }

    private String sparqlForAsset(String assetIri) {
        return "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <" + PROP_CREDENTIAL_SUBJECT + "> <" + assetIri + "> }";
    }

    private String postQuery(String sparqlQuery) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/query")
                        .content(sparqlQuery)
                        .contentType(SPARQL_CONTENT_TYPE)
                        .header("Accept", "application/json")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
