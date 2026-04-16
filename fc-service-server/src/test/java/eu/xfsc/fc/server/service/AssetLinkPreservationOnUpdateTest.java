package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkRepository;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkType;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.assetlink.AssetLinkService;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.util.HashUtils;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests verifying that asset links are preserved after operations
 * that do not explicitly remove them (AC-4.2, AC-4.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetLinkPreservationOnUpdateTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String MR_IRI = "did:web:test:preservation-mr";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;
  @Autowired
  private AssetLinkService assetLinkService;
  @Autowired
  private AssetLinkRepository assetLinkRepository;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    assetStore.clear();
    assetLinkRepository.deleteAll();
  }

  /**
   * AC-4.2: Updating the machine-readable asset (creating a new version) must not remove link rows.
   * Link rows reference asset IRIs, not content hashes, so they survive hash changes.
   */
  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void updateMachineReadableAsset_linksPreservedAfterUpdate() throws Exception {
    // Store MR asset (v1) directly to obtain a stable IRI for the link
    storeMrVersion("initial MR content v1");

    // Upload HR linked to MR
    final var hrAsset = uploadHumanReadable(MR_IRI, "initial HR content", "text/plain", "hr.txt");

    final var linksBefore = assetLinkRepository.findAll();
    assertEquals(2, linksBefore.size(), "Both bidirectional link rows must exist before update");

    // Update the MR asset (v2): same IRI, different hash — link rows must survive
    storeMrVersion("updated MR content v2");

    final var linksAfterUpdate = assetLinkRepository.findAll();
    assertEquals(2, linksAfterUpdate.size(),
        "Link rows must still be present after updating the MR asset");
    assertTrue(linksAfterUpdate.stream()
        .anyMatch(l -> l.getSourceId().equals(MR_IRI)
            && l.getLinkType() == AssetLinkType.HAS_HUMAN_READABLE),
        "HAS_HUMAN_READABLE link must still point from MR to HR IRI");

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  // ===== helpers =====

  /**
   * Store a version of the machine-readable asset directly, bypassing HTTP verification.
   * Uses {@link AssetStore#storeCredential} to simulate a PUT /assets/{id} update.
   */
  private void storeMrVersion(String content) {
    String hash = HashUtils.calculateSha256AsHex(content);
    AssetMetadata meta = new AssetMetadata();
    meta.setId(MR_IRI);
    meta.setIssuer(TEST_ISSUER);
    meta.setAssetHash(hash);
    meta.setStatus(AssetStatus.ACTIVE);
    meta.setUploadDatetime(Instant.now());
    meta.setStatusDatetime(Instant.now());
    meta.setContentAccessor(new ContentAccessorDirect(content));
    CredentialVerificationResult vr = new CredentialVerificationResult(
        Instant.now(), AssetStatus.ACTIVE.getValue(), TEST_ISSUER, Instant.now(), MR_IRI,
        List.of(), List.of());
    assetStore.storeCredential(meta, vr);
  }

  private Asset uploadHumanReadable(String mrIri, String text, String contentType, String filename) throws Exception {
    final var content = text.getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", filename, contentType, content);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart("/assets/" + URLEncoder.encode(mrIri, StandardCharsets.UTF_8) + "/human-readable")
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
}
