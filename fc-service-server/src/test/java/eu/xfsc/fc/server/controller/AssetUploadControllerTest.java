package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

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

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetUploadControllerTest {

    private static final String TEST_ISSUER = "http://example.org/test-issuer";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssetStore assetStorePublisher;

    @BeforeAll
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void deleteAssetQuietly(String hash) {
        try {
            assetStorePublisher.deleteAsset(hash);
        } catch (NotFoundException e) {
            // expected
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPlainTextMultipartReturnsCreated() throws Exception {
        byte[] content = "Hello, this is a plain text template.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "template.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertNotNull(asset.getId());
        assertTrue(asset.getId().startsWith("urn:uuid:"), "Non-RDF asset ID should be UUID URN, got: " + asset.getId());
        assertTrue(asset.getId().matches("urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "UUID URN should match RFC 4122 format, got: " + asset.getId());
        assertEquals("text/plain", asset.getContentType());
        assertEquals(content.length, asset.getFileSize());
        assertNull(asset.getValidatorDids());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPdfMultipartReturnsCreated() throws Exception {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A}; // %PDF-1.4\n
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("application/pdf", asset.getContentType());
        assertEquals(HashUtils.calculateSha256AsHex(content), asset.getAssetHash());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPlainJsonNoContextReturnsCreated() throws Exception {
        byte[] content = "{\"name\": \"contract\", \"version\": 1}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "contract.json", "application/json", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("application/json", asset.getContentType());
        assertNull(asset.getValidatorDids());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadOctetStreamReturnsCreated() throws Exception {
        byte[] content = "raw binary content for testing".getBytes(StandardCharsets.UTF_8);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(content)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals(HashUtils.calculateSha256AsHex(content), asset.getAssetHash());
        assertEquals(content.length, asset.getFileSize());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadDuplicateAssetReturnsConflict() throws Exception {
        byte[] content = "duplicate-test-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "dup.txt", "text/plain", content);

        MvcResult firstResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(firstResult.getResponse().getContentAsString(), Asset.class);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_withWrongRole_returnsForbidden() throws Exception {
        byte[] content = "wrong role test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void uploadMultipartWithoutAuthReturnsUnauthorized() throws Exception {
        byte[] content = "unauthorized test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipartWithAssetAdminRoleReturnsCreated() throws Exception {
        byte[] content = "asset-admin upload test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "admin.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAssetWithJsonLdReturnsOkWithEnrichmentResponse() throws Exception {
        // 1. Create initial non-RDF asset
        byte[] plainContent = "plain text document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);
        String assetId = createdAsset.getId();

        try {
            // 2. Enrich with RDF metadata
            String rdfPayload = "{"
                + "  \"@context\": {\"ex\": \"http://example.org/\"},"
                + "  \"@id\": \"" + assetId + "\","
                + "  \"ex:title\": \"Enriched Document\","
                + "  \"ex:creator\": \"Test User\""
                + "}";
            byte[] rdfContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "metadata.jsonld", "application/ld+json", rdfContent);

            MvcResult enrichResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            AssetEnrichmentResponse response = objectMapper.readValue(enrichResult.getResponse().getContentAsString(), AssetEnrichmentResponse.class);
            assertNotNull(response);
            assertEquals(assetId, response.getAssetId());
            assertEquals(2, response.getTriplesAdded()); // @id, @type for the object
            assertEquals(0, response.getTriplesRejected());
            assertNotNull(response.getEnrichmentTimestamp());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAssetWithTurtleReturnsOkWithEnrichmentResponse() throws Exception {
        // 1. Create initial non-RDF asset
        byte[] plainContent = "binary data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "data.bin", "application/octet-stream", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);
        String assetId = createdAsset.getId();

        try {
            // 2. Enrich with Turtle RDF
            String turtlePayload = "@prefix ex: <http://example.org/> .\n"
                + "<" + assetId + "> ex:hasVersion \"1.0\" .\n"
                + "<" + assetId + "> ex:hasStatus \"active\" .";
            byte[] rdfContent = turtlePayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "metadata.ttl", "text/turtle", rdfContent);

            MvcResult enrichResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            AssetEnrichmentResponse response = objectMapper.readValue(enrichResult.getResponse().getContentAsString(), AssetEnrichmentResponse.class);
            assertNotNull(response);
            assertEquals(assetId, response.getAssetId());
            assertEquals(2, response.getTriplesAdded()); // two explicit triples
            assertEquals(0, response.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAssetViaMultipartWithTurtleReturnsOkWithEnrichmentResponse() throws Exception {
        // 1. Create initial non-RDF asset
        byte[] plainContent = "document content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);
        String assetId = createdAsset.getId();

        try {
            // 2. Enrich via multipart with Turtle RDF
            String turtlePayload = "@prefix ex: <http://example.org/> .\n"
                + "<" + assetId + "> ex:description \"Enriched via multipart\" .";
            byte[] rdfContent = turtlePayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "metadata.ttl", "text/turtle", rdfContent);

            MvcResult enrichResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            AssetEnrichmentResponse response = objectMapper.readValue(enrichResult.getResponse().getContentAsString(), AssetEnrichmentResponse.class);
            assertNotNull(response);
            assertEquals(assetId, response.getAssetId());
            assertTrue(response.getTriplesAdded() > 0);
            assertEquals(0, response.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAssetWithDifferentSubjectCreatesNewRdfAsset() throws Exception {
        // 1. Create initial non-RDF asset
        byte[] plainContent = "document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);

        try {
            // 2. Upload RDF with different subject — creates a new RDF asset, not enrichment
            String rdfPayload = "{"
                + "  \"@context\": {\"ex\": \"http://example.org/\"},"
                + "  \"@id\": \"http://different.org/asset/123\","
                + "  \"ex:title\": \"Different Asset\""
                + "}";
            byte[] rdfContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "metadata.jsonld", "application/ld+json", rdfContent);

            MvcResult newAssetResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

            // Verify new asset was created (different from original)
            Asset newAsset = objectMapper.readValue(newAssetResult.getResponse().getContentAsString(), Asset.class);
            assertNotEquals(createdAsset.getId(), newAsset.getId());
            deleteAssetQuietly(newAsset.getAssetHash());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAssetWithInvalidRdfReturnsBadRequest() throws Exception {
        // 1. Create initial non-RDF asset
        byte[] plainContent = "document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);

        try {
            // 2. Try to enrich with malformed RDF
            byte[] invalidRdf = "{ invalid json-ld }".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "bad.jsonld", "application/ld+json", invalidRdf);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichHumanReadableAsset_returnsUnprocessableEntity() throws Exception {
        // 1. Create a non-RDF (machine-readable) asset
        byte[] mrContent = "machine-readable document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile mrFile = new MockMultipartFile("file", "doc.txt", "text/plain", mrContent);

        MvcResult mrResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(mrFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset mrAsset = objectMapper.readValue(mrResult.getResponse().getContentAsString(), Asset.class);
        String mrId = mrAsset.getId();

        // 2. Upload a human-readable asset linked to the MR asset
        byte[] hrContent = "<html><body>Human Readable</body></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile hrFile = new MockMultipartFile("file", "doc.html", "text/html", hrContent);
        String encodedMrId = java.net.URLEncoder.encode(mrId, StandardCharsets.UTF_8);

        MvcResult hrResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets/" + encodedMrId + "/human-readable")
                .file(hrFile)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset hrAsset = objectMapper.readValue(hrResult.getResponse().getContentAsString(), Asset.class);
        String hrId = hrAsset.getId();

        try {
            // 3. Attempt to enrich the HR asset with RDF — must return 422
            String rdfPayload = "@prefix ex: <http://example.org/> .\n"
                + "<" + hrId + "> ex:title \"HR Document\" .";
            byte[] rdfContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "meta.ttl", "text/turtle", rdfContent);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity());
        } finally {
            deleteAssetQuietly(hrAsset.getAssetHash());
            deleteAssetQuietly(mrAsset.getAssetHash());
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void enrichNonRdfAsset_reEnriched_priorTriplesReplaced() throws Exception {
        // 1. Create initial non-RDF asset
        byte[] plainContent = "document content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);
        String assetId = createdAsset.getId();

        try {
            // 2. First enrichment: 1 triple
            String firstPayload = "@prefix ex: <http://example.org/> .\n"
                + "<" + assetId + "> ex:version \"1.0\" .";
            MockMultipartFile rdfFile1 = new MockMultipartFile("file", "v1.ttl", "text/turtle",
                firstPayload.getBytes(StandardCharsets.UTF_8));

            MvcResult firstEnrich = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile1)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            AssetEnrichmentResponse first = objectMapper.readValue(firstEnrich.getResponse().getContentAsString(),
                AssetEnrichmentResponse.class);
            assertEquals(1, first.getTriplesAdded());

            // 3. Second enrichment: 2 triples — prior triples must be replaced
            String secondPayload = "@prefix ex: <http://example.org/> .\n"
                + "<" + assetId + "> ex:version \"2.0\" .\n"
                + "<" + assetId + "> ex:status \"active\" .";
            MockMultipartFile rdfFile2 = new MockMultipartFile("file", "v2.ttl", "text/turtle",
                secondPayload.getBytes(StandardCharsets.UTF_8));

            MvcResult secondEnrich = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile2)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

            AssetEnrichmentResponse second = objectMapper.readValue(secondEnrich.getResponse().getContentAsString(),
                AssetEnrichmentResponse.class);
            assertEquals(assetId, second.getAssetId());
            assertEquals(2, second.getTriplesAdded());
            assertEquals(0, second.getTriplesRejected());
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }

}