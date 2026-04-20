package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.AfterEach;
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
 * Integration tests for {@link AssetLinkController}.
 * Exercises POST /assets/{id}/human-readable, GET /assets/{id}/human-readable,
 * GET /assets/{id}/machine-readable and security constraints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetLinkControllerTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String HR_URL_TEMPLATE = "/assets/%s/human-readable";
  private static final String MR_URL_TEMPLATE = "/assets/%s/machine-readable";

  /** Minimal valid PDF header bytes. */
  private static final byte[] PDF_CONTENT = "%PDF-1.4\n%test asset".getBytes(StandardCharsets.UTF_8);
  private static final byte[] DOCX_CONTENT = "DOCX content for test".getBytes(StandardCharsets.UTF_8);
  private static final byte[] HTML_CONTENT = "<html><body>test</body></html>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TXT_CONTENT = "Plain text human-readable representation".getBytes(StandardCharsets.UTF_8);

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;

  /** IRI of the machine-readable parent asset created before each test. */
  private String mrIri;
  /** Hash of the machine-readable parent asset. */
  private String mrHash;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    // Best-effort cleanup: delete both MR and any linked HR asset.
    if (mrHash != null) {
      deleteAssetQuietly(mrHash);
      mrHash = null;
    }
    mrIri = null;
  }

  // ===== POST /assets/{id}/human-readable — success cases =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_pdf_returnsCreated() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    final var hrAsset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNotNull(hrAsset.getId());
    assertNotNull(hrAsset.getAssetHash());
    assertEquals("application/pdf", hrAsset.getContentType());

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_docx_returnsCreated() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    final var file = new MockMultipartFile("file", "doc.docx", contentType, DOCX_CONTENT);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    final var hrAsset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNotNull(hrAsset.getId());
    assertEquals(contentType, hrAsset.getContentType());

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_html_returnsCreated() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "page.html", "text/html", HTML_CONTENT);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    final var hrAsset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNotNull(hrAsset.getId());

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_txt_returnsCreated() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "readme.txt", "text/plain", TXT_CONTENT);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    final var hrAsset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNotNull(hrAsset.getId());

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  // ===== POST /assets/{id}/human-readable — error cases =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_unsupportedContentType_returnsBadRequest() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "img.png", "image/png",
        "fake png data".getBytes(StandardCharsets.UTF_8));

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andReturn();

    // Error message must list accepted content types
    final var body = result.getResponse().getContentAsString();
    assertTrue(body.contains("application/pdf") || body.contains("Accepted"),
        "Error response must include accepted content types, got: " + body);
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_unknownParentAsset_returnsNotFound() throws Exception {
    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);

    mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, "urn:uuid:does-not-exist"))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_reupload_differentContent_returnsCreated() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    final var firstResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrV1 = objectMapper.readValue(firstResult.getResponse().getContentAsString(), Asset.class);

    final var file2 = new MockMultipartFile("file", "doc2.pdf", "application/pdf",
        "different content".getBytes(StandardCharsets.UTF_8));
    final var secondResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file2)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrV2 = objectMapper.readValue(secondResult.getResponse().getContentAsString(), Asset.class);

    assertEquals(hrV1.getId(), hrV2.getId(), "Re-upload must reuse the same HR IRI");

    deleteAssetQuietly(hrV2.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_reupload_sameContent_returnsConflict() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    final var firstResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrAsset = objectMapper.readValue(firstResult.getResponse().getContentAsString(), Asset.class);

    final var file2 = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file2)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict());

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  // ===== GET /assets/{id}/human-readable =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getHumanReadable_afterUpload_returnsFileContent() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    final var uploadResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrAsset = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Asset.class);

    final var getResult = mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andReturn();

    final var responseBody = getResult.getResponse().getContentAsByteArray();
    assertEquals(PDF_CONTENT.length, responseBody.length);

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getHumanReadable_noLinkExists_returnsNotFound() throws Exception {
    // Use ASSET_READ — this user cannot create, they can only read.
    // We test GET against a real (but unlinked) asset IRI via a dummy UUID.
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(HR_URL_TEMPLATE, "urn:uuid:unlinked-asset"))
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  // ===== GET /assets/{id}/machine-readable =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getMachineReadable_afterUpload_returnsFileContent() throws Exception {
    final var mrAsset = uploadMachineReadableNonRdfAsset();
    mrIri = mrAsset.getId();
    mrHash = mrAsset.getAssetHash();

    // Upload HR linking to MR
    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    final var uploadResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrIri)))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrAsset = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Asset.class);

    // Now get MR via HR IRI
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(MR_URL_TEMPLATE, encode(hrAsset.getId())))
            .with(csrf()))
        .andExpect(status().isOk())
        .andReturn();

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getMachineReadable_noLinkExists_returnsNotFound() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(MR_URL_TEMPLATE, "urn:uuid:has-no-mr-link"))
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  // ===== Security =====

  @Test
  void uploadHumanReadable_unauthenticated_returnsUnauthorized() throws Exception {
    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);

    mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, "urn:uuid:any"))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_wrongRole_returnsForbidden() throws Exception {
    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);

    mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, "urn:uuid:any"))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getHumanReadable_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(HR_URL_TEMPLATE, "urn:uuid:any"))
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getMachineReadable_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(MR_URL_TEMPLATE, "urn:uuid:any"))
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwtAuth(authorities = {ADMIN_ALL_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadHumanReadable_adminAllRole_returnsNotFoundForUnknownParent() throws Exception {
    // ADMIN_ALL has permission — request reaches the controller and gets 404 for unknown MR asset.
    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);

    mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, "urn:uuid:unknown-parent"))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void uploadAsset_withHasHumanReadableTriple_stripsTripleAndReturnsWarning() throws Exception {
    // An external upload that contains a fcmeta:hasHumanReadable
    // triple must have the triple silently stripped and a user-visible warning returned.
    // The protected namespace must not be settable by external clients.
    final var jsonLd = """
        {
          "@context": ["https://www.w3.org/ns/credentials/v2"],
          "type": "VerifiablePresentation",
          "id": "presentationID",
          "verifiableCredential": [{
            "@context": ["https://www.w3.org/ns/credentials/v2"],
            "id": "http://example.edu/credentials/link-inject-test",
            "type": "VerifiableCredential",
            "issuer": "http://example.org/test-issuer",
            "validFrom": "2010-01-01T19:53:24Z",
            "credentialSubject": {
              "@id": "http://example.org/test-issuer",
              "@type": "https://w3id.org/gaia-x/2511#ServiceOffering",
              "gx:hasLegallyBindingName": "link inject test",
              "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#hasHumanReadable":
                  "http://example.org/some-pdf"
            }
          }]
        }
        """;

    final var result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
            .content(jsonLd)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    final var asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);

    assertNotNull(asset.getWarnings(), "Warning must be present when fcmeta link triple is filtered");
    assertFalse(asset.getWarnings().isEmpty(), "Warnings list must not be empty");
    assertTrue(asset.getWarnings().getFirst().contains("triple(s)"),
        "Warning must state how many triples were filtered");
    assertTrue(asset.getWarnings().getFirst().contains("federated-catalogue/meta#"),
        "Warning must reference the protected namespace URI");

    deleteAssetQuietly(asset.getAssetHash());
  }

  // ===== Helpers =====

  /**
   * Upload a non-RDF (plain-text) asset to obtain a machine-readable parent IRI for tests.
   * The caller is responsible for cleanup via {@link #deleteAssetQuietly(String)}.
   */
  private Asset uploadMachineReadableAsset() throws Exception {
    final var content = "machine-readable plain text asset for link tests".getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", "asset.txt", "text/plain", content);

    final var result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  /**
   * Upload a non-RDF binary asset so its file content can be streamed back from GET /machine-readable.
   */
  private Asset uploadMachineReadableNonRdfAsset() throws Exception {
    final var content = "binary-mr-content-for-link-test".getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", "mr.bin", "application/octet-stream", content);

    final var result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  private void deleteAssetQuietly(String hash) {
    try {
      assetStore.deleteAsset(hash);
    } catch (NotFoundException e) {
      // expected
    }
  }

  private static String encode(String iri) {
    return java.net.URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }
}
