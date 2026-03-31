package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Integration tests for Asset Type Admin endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetTypeAdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AssetDao assetDao;

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getAssetTypeConfig_withAdminRole_returnsConfig() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/asset-types")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.allowedTypes").isArray());
  }

  @Test
  @WithMockUser
  void getAssetTypeConfig_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/asset-types")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void updateAssetTypeConfig_withAdminRole_returns200() throws Exception {
    String body = "{\"enabled\":true,\"allowedTypes\":[\"VerifiableCredential\"]}";

    mockMvc.perform(MockMvcRequestBuilders.put("/admin/asset-types")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isOk());

    // Verify the config was saved
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/asset-types")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.allowedTypes[0]").value("VerifiableCredential"));

    // Reset
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/asset-types")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false,\"allowedTypes\":[]}")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getExistingAssetTypes_emptyDb_returnsEmptyArray() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/asset-types/existing")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getExistingAssetTypes_withAssets_returnsDistinctTypes() throws Exception {
    Instant now = Instant.now();
    AssetRecord r1 = AssetRecord.builder()
        .assetHash("test-hash-1").id("sub/1").status(AssetStatus.ACTIVE)
        .issuer("iss/1").uploadTime(now).statusTime(now)
        .content(new ContentAccessorDirect("c1"))
        .credentialTypes(List.of("VerifiablePresentation", "ServiceOffering"))
        .build();
    AssetRecord r2 = AssetRecord.builder()
        .assetHash("test-hash-2").id("sub/2").status(AssetStatus.ACTIVE)
        .issuer("iss/2").uploadTime(now).statusTime(now)
        .content(new ContentAccessorDirect("c2"))
        .credentialTypes(List.of("VerifiablePresentation", "LegalParticipant"))
        .build();

    try {
      assetDao.insert(r1);
      assetDao.insert(r2);

      mockMvc.perform(MockMvcRequestBuilders.get("/admin/asset-types/existing")
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(3)))
          .andExpect(jsonPath("$", hasItem("VerifiablePresentation")))
          .andExpect(jsonPath("$", hasItem("ServiceOffering")))
          .andExpect(jsonPath("$", hasItem("LegalParticipant")));
    } finally {
      assetDao.delete("test-hash-1");
      assetDao.delete("test-hash-2");
    }
  }

  @Test
  void getAssetTypeConfig_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/asset-types")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
