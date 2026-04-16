package eu.xfsc.fc.core.dao.assetlinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.pojo.AssetLinkType;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetLinkRepositoryTest.TestConfig.class,
    DatabaseConfig.class, SecurityAuditorAware.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetLinkRepositoryTest {

  private static final String SOURCE_IRI = "urn:uuid:source-001";
  private static final String TARGET_IRI = "urn:uuid:target-001";
  private static final String OTHER_SOURCE_IRI = "urn:uuid:source-002";
  private static final String OTHER_TARGET_IRI = "urn:uuid:target-002";

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private AssetLinkRepository assetLinkRepository;

  @AfterEach
  void cleanUp() {
    assetLinkRepository.deleteAll();
  }

  // ===== findBySourceId =====

  @Test
  void findBySourceId_existingSource_returnsLinks() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    List<AssetLink> links = assetLinkRepository.findBySourceId(SOURCE_IRI);

    assertEquals(1, links.size());
    assertEquals(SOURCE_IRI, links.getFirst().getSourceId());
    assertEquals(TARGET_IRI, links.getFirst().getTargetId());
    assertEquals(AssetLinkType.HAS_HUMAN_READABLE, links.getFirst().getLinkType());
  }

  @Test
  void findBySourceId_unknownSource_returnsEmptyList() {
    List<AssetLink> links = assetLinkRepository.findBySourceId("urn:uuid:does-not-exist");

    assertTrue(links.isEmpty());
  }

  @Test
  void findBySourceId_multipleLinks_returnsAll() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));
    assetLinkRepository.save(buildLink(SOURCE_IRI, OTHER_TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    List<AssetLink> links = assetLinkRepository.findBySourceId(SOURCE_IRI);

    assertEquals(2, links.size());
  }

  // ===== findByTargetId =====

  @Test
  void findByTargetId_existingTarget_returnsLinks() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_MACHINE_READABLE));

    List<AssetLink> links = assetLinkRepository.findByTargetId(TARGET_IRI);

    assertEquals(1, links.size());
    assertEquals(SOURCE_IRI, links.getFirst().getSourceId());
    assertEquals(AssetLinkType.HAS_MACHINE_READABLE, links.getFirst().getLinkType());
  }

  @Test
  void findByTargetId_unknownTarget_returnsEmptyList() {
    List<AssetLink> links = assetLinkRepository.findByTargetId("urn:uuid:no-such-target");

    assertTrue(links.isEmpty());
  }

  // ===== findBySourceIdAndLinkType =====

  @Test
  void findBySourceIdAndLinkType_matchingEntry_returnsLink() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    Optional<AssetLink> result = assetLinkRepository.findBySourceIdAndLinkType(
        SOURCE_IRI, AssetLinkType.HAS_HUMAN_READABLE);

    assertTrue(result.isPresent());
    assertEquals(TARGET_IRI, result.get().getTargetId());
  }

  @Test
  void findBySourceIdAndLinkType_wrongLinkType_returnsEmpty() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    Optional<AssetLink> result = assetLinkRepository.findBySourceIdAndLinkType(
        SOURCE_IRI, AssetLinkType.HAS_MACHINE_READABLE);

    assertFalse(result.isPresent());
  }

  @Test
  void findBySourceIdAndLinkType_noEntry_returnsEmpty() {
    Optional<AssetLink> result = assetLinkRepository.findBySourceIdAndLinkType(
        "urn:uuid:nonexistent", AssetLinkType.HAS_HUMAN_READABLE);

    assertFalse(result.isPresent());
  }

  // ===== deleteBySourceIdOrTargetId =====

  @Test
  void deleteBySourceIdOrTargetId_deletesAllMatchingRows() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));
    assetLinkRepository.save(buildLink(TARGET_IRI, SOURCE_IRI, AssetLinkType.HAS_MACHINE_READABLE));
    assetLinkRepository.save(buildLink(OTHER_SOURCE_IRI, OTHER_TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    assetLinkRepository.deleteBySourceIdOrTargetId(SOURCE_IRI, SOURCE_IRI);

    List<AssetLink> remaining = assetLinkRepository.findAll();
    assertEquals(1, remaining.size());
    assertEquals(OTHER_SOURCE_IRI, remaining.getFirst().getSourceId());
  }

  @Test
  void deleteBySourceIdOrTargetId_noMatchingRows_deletesNothing() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    assetLinkRepository.deleteBySourceIdOrTargetId("urn:uuid:nobody", "urn:uuid:nobody");

    assertEquals(1, assetLinkRepository.count());
  }

  // ===== unique constraint =====

  @Test
  void save_duplicateLink_uniqueConstraintPreventsSecondInsert() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));

    AssetLink duplicate = buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE);

    org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
        () -> {
          assetLinkRepository.save(duplicate);
          assetLinkRepository.flush();
        },
        "Duplicate link entry should be rejected by the unique constraint");
  }

  // ===== linkType enum stored as STRING =====

  @Test
  void save_linkTypeStoredAsString_canBeRetrievedByEnumValue() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));
    assetLinkRepository.save(buildLink(OTHER_SOURCE_IRI, OTHER_TARGET_IRI, AssetLinkType.HAS_MACHINE_READABLE));

    Optional<AssetLink> hr = assetLinkRepository.findBySourceIdAndLinkType(
        SOURCE_IRI, AssetLinkType.HAS_HUMAN_READABLE);
    Optional<AssetLink> mr = assetLinkRepository.findBySourceIdAndLinkType(
        OTHER_SOURCE_IRI, AssetLinkType.HAS_MACHINE_READABLE);

    assertTrue(hr.isPresent());
    assertTrue(mr.isPresent());
    assertEquals(AssetLinkType.HAS_HUMAN_READABLE, hr.get().getLinkType());
    assertEquals(AssetLinkType.HAS_MACHINE_READABLE, mr.get().getLinkType());
  }

  // ===== findAll (for graph rebuild) =====

  @Test
  void findAll_mixedLinks_returnsAllRows() {
    assetLinkRepository.save(buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE));
    assetLinkRepository.save(buildLink(TARGET_IRI, SOURCE_IRI, AssetLinkType.HAS_MACHINE_READABLE));

    List<AssetLink> all = assetLinkRepository.findAll();

    assertEquals(2, all.size());
  }

  // ===== field values =====

  @Test
  void save_allFields_persistedCorrectly() {
    final var link = buildLink(SOURCE_IRI, TARGET_IRI, AssetLinkType.HAS_HUMAN_READABLE);
    link.setCreatedBy("did:example:creator");

    assetLinkRepository.save(link);

    final var saved = assetLinkRepository.findBySourceIdAndLinkType(
        SOURCE_IRI, AssetLinkType.HAS_HUMAN_READABLE).orElseThrow();

    assertNotNull(saved.getId());
    assertEquals(SOURCE_IRI, saved.getSourceId());
    assertEquals(TARGET_IRI, saved.getTargetId());
    assertEquals(AssetLinkType.HAS_HUMAN_READABLE, saved.getLinkType());
    assertNotNull(saved.getCreatedAt());
    assertEquals("did:example:creator", saved.getCreatedBy());
  }

  // ===== helper =====

  private static AssetLink buildLink(String sourceId, String targetId, AssetLinkType linkType) {
    final var link = new AssetLink();
    link.setSourceId(sourceId);
    link.setTargetId(targetId);
    link.setLinkType(linkType);
    return link;
  }
}
