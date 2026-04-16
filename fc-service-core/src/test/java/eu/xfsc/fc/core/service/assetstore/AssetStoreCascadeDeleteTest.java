package eu.xfsc.fc.core.service.assetstore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkRepository;
import eu.xfsc.fc.core.pojo.AssetLinkType;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.assetlink.AssetLinkService;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
import eu.xfsc.fc.core.util.HashUtils;
import eu.xfsc.fc.core.service.assetstore.IriValidator;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration tests for cascade-delete behaviour in {@link AssetStoreImpl}.
 *
 * <p>When a machine-readable (MR) asset is deleted, its linked human-readable (HR) asset
 * must be deleted too. When a HR asset is deleted directly, the MR asset must survive.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetStoreCascadeDeleteTest.TestConfig.class, AssetStoreImpl.class,
    AssetJpaDao.class, AssetAuditRepository.class, DatabaseConfig.class, SecurityAuditorAware.class,
    DummyGraphStore.class, FileStoreConfig.class, AssetLinkService.class, IriGenerator.class,
    ProtectedNamespaceProperties.class, IriValidator.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetStoreCascadeDeleteTest {

  private static final String TEST_ISSUER = "did:example:test-issuer";
  private static final String MR_ID = "urn:uuid:mr-asset-cascade-01";

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private AssetStore assetStore;

  @Autowired
  private AssetLinkService assetLinkService;

  @Autowired
  private AssetLinkRepository assetLinkRepository;

  @AfterEach
  void cleanUp() {
    assetStore.clear();
    assetLinkRepository.deleteAll();
  }

  // ===== delete MR asset cascade-deletes HR =====

  @Test
  void deleteAsset_machineReadableWithLinkedHumanReadable_deletesHumanReadableToo() {
    final var mrMeta = storeNonRdfAsset(MR_ID, "mr content");
    final var hrMeta = storeNonRdfAsset(null, "hr content");

    assetLinkService.createLink(mrMeta.getId(), hrMeta.getId(), AssetLinkType.HAS_HUMAN_READABLE);

    assetStore.deleteAsset(mrMeta.getAssetHash());

    assertThrows(NotFoundException.class, () -> assetStore.getByHash(mrMeta.getAssetHash()),
        "MR asset must be gone after delete");
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(hrMeta.getAssetHash()),
        "HR asset must be cascade-deleted when MR parent is deleted");
    assertTrue(assetLinkRepository.findAll().isEmpty(),
        "All link rows must be removed after cascade delete");
  }

  // ===== delete HR asset directly preserves MR =====

  @Test
  void deleteAsset_humanReadableDirectly_preservesMachineReadable() {
    final var mrMeta = storeNonRdfAsset(MR_ID, "mr content v2");
    final var hrMeta = storeNonRdfAsset(null, "hr content v2");

    assetLinkService.createLink(mrMeta.getId(), hrMeta.getId(), AssetLinkType.HAS_HUMAN_READABLE);

    assetStore.deleteAsset(hrMeta.getAssetHash());

    assertNotNull(assetStore.getByHash(mrMeta.getAssetHash()),
        "MR asset must survive when HR asset is deleted directly");
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(hrMeta.getAssetHash()),
        "HR asset must be gone after direct delete");
    assertTrue(assetLinkRepository.findAll().isEmpty(),
        "Link rows must be cleaned up after HR is deleted directly");
  }

  // ===== delete unlinked asset — no cascade side effects =====

  @Test
  void deleteAsset_noLinksExist_deletesSuccessfully() {
    final var meta = storeNonRdfAsset(null, "standalone asset no links");

    assetStore.deleteAsset(meta.getAssetHash());

    assertThrows(NotFoundException.class, () -> assetStore.getByHash(meta.getAssetHash()));
    assertTrue(assetLinkRepository.findAll().isEmpty());
  }

  // ===== helper =====

  private AssetMetadata storeNonRdfAsset(String id, String contentText) {
    final var content = contentText.getBytes(StandardCharsets.UTF_8);
    final var hash = HashUtils.calculateSha256AsHex(content);
    final var now = Instant.now();
    final var contentAccessor = new ContentAccessorBinary(content);

    final var meta = new AssetMetadata(hash, id, AssetStatus.ACTIVE,
        TEST_ISSUER, null, now, now, contentAccessor);
    meta.setContentType("application/octet-stream");
    meta.setFileSize((long) content.length);

    return assetStore.storeUnverified(meta, contentText.replaceAll(" ", "_") + ".bin");
  }
}
