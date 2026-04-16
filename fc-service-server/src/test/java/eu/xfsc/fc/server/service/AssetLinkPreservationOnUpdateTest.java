package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import eu.xfsc.fc.core.pojo.AssetLinkType;
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

  /**
   * AC-4.3: The human-readable representation can be updated (replaced) separately
   * without changing the machine-readable asset.
   *
   * <p>Steps: upload MR + HR v1 → delete HR v1 → upload HR v2 linked to same MR →
   * assert MR is unchanged and link now points to HR v2.</p>
   */
  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void replaceHumanReadableAsset_machineReadableAssetUnchanged() throws Exception {
    storeMrVersion("MR content for AC-4.3");

    final var hrV1 = uploadHumanReadable(MR_IRI, "HR v1 content", "text/plain", "hr-v1.txt");
    assertEquals(2, assetLinkRepository.count(),
        "Both bidirectional link rows must exist after initial upload");

    // Delete HR v1 — this must remove link rows but leave the MR asset intact
    deleteAssetQuietly(hrV1.getAssetHash());
    assertEquals(0, assetLinkRepository.count(), "Link rows must be removed after HR deletion");

    // Upload HR v2 linked to the same MR
    final var hrV2 = uploadHumanReadable(MR_IRI, "HR v2 content", "text/plain", "hr-v2.txt");

    // MR asset must still exist and be unchanged
    final var mrMeta = assetStore.getById(MR_IRI);
    assertNotNull(mrMeta, "MR asset must still exist after replacing the HR asset");
    assertEquals(TEST_ISSUER, mrMeta.getIssuer(), "MR asset issuer must be unchanged");

    // Link must now point to HR v2
    final var linkedHrIri = assetLinkService.getLinkedAsset(MR_IRI, AssetLinkType.HAS_HUMAN_READABLE);
    assertTrue(linkedHrIri.isPresent(), "A new HAS_HUMAN_READABLE link must exist after re-upload");
    assertEquals(hrV2.getId(), linkedHrIri.get(),
        "Link must point to HR v2, not the deleted HR v1");

    deleteAssetQuietly(hrV2.getAssetHash());
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
