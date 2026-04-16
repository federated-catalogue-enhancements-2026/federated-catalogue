package eu.xfsc.fc.core.service.assetlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assetlinks.AssetLink;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkRepository;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkType;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
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
 * Integration tests for {@link AssetLinkService}.
 * Uses embedded PostgreSQL and a dummy (no-op) graph store.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetLinkServiceTest.TestConfig.class, AssetLinkService.class,
    AssetJpaDao.class, AssetAuditRepository.class, DatabaseConfig.class,
    SecurityAuditorAware.class, DummyGraphStore.class, FileStoreConfig.class,
    ProtectedNamespaceProperties.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetLinkServiceTest {

  private static final String MR_IRI = "urn:uuid:machine-readable-001";
  private static final String HR_IRI = "urn:uuid:human-readable-001";
  private static final String CREATOR_DID = "did:example:creator";
  private static final String OTHER_HR_IRI = "urn:uuid:human-readable-002";

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private AssetLinkService assetLinkService;

  @Autowired
  private AssetLinkRepository assetLinkRepository;

  @AfterEach
  void cleanUp() {
    assetLinkRepository.deleteAll();
  }

  // ===== createLink =====

  @Test
  void createLink_newBidirectionalLink_insertsTwoRows() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);

    final var forwardLink = assetLinkRepository.findBySourceIdAndLinkType(
        MR_IRI, AssetLinkType.HAS_HUMAN_READABLE);
    final var reverseLink = assetLinkRepository.findBySourceIdAndLinkType(
        HR_IRI, AssetLinkType.HAS_MACHINE_READABLE);

    assertTrue(forwardLink.isPresent());
    assertEquals(HR_IRI, forwardLink.get().getTargetId());

    assertTrue(reverseLink.isPresent());
    assertEquals(MR_IRI, reverseLink.get().getTargetId());
  }

  @Test
  void createLink_newLink_setsCreatedBy() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);

    final var link = assetLinkRepository.findBySourceIdAndLinkType(
        MR_IRI, AssetLinkType.HAS_HUMAN_READABLE).orElseThrow();

    assertEquals(CREATOR_DID, link.getCreatedBy());
  }

  @Test
  void createLink_duplicateLinkType_throwsConflict() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);

    assertThrows(ConflictException.class,
        () -> assetLinkService.createLink(MR_IRI, OTHER_HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID),
        "Second HAS_HUMAN_READABLE link for the same MR asset should be rejected");
  }

  // ===== getLinkedAsset =====

  @Test
  void getLinkedAsset_existingLink_returnsTargetIri() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);

    final var result = assetLinkService.getLinkedAsset(MR_IRI, AssetLinkType.HAS_HUMAN_READABLE);

    assertTrue(result.isPresent());
    assertEquals(HR_IRI, result.get());
  }

  @Test
  void getLinkedAsset_reverseDirection_resolvesMachineReadable() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);

    final var result = assetLinkService.getLinkedAsset(HR_IRI, AssetLinkType.HAS_MACHINE_READABLE);

    assertTrue(result.isPresent());
    assertEquals(MR_IRI, result.get());
  }

  @Test
  void getLinkedAsset_noLink_returnsEmpty() {
    final var result = assetLinkService.getLinkedAsset("urn:uuid:unlinked", AssetLinkType.HAS_HUMAN_READABLE);

    assertFalse(result.isPresent());
  }

  // ===== deleteLinksForAsset =====

  @Test
  void deleteLinksForAsset_existingBidirectionalLinks_removesBothRows() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);
    assertEquals(2, assetLinkRepository.count());

    assetLinkService.deleteLinksForAsset(MR_IRI);

    assertEquals(0, assetLinkRepository.count());
  }

  @Test
  void deleteLinksForAsset_deletingHrAsset_removesLinks_leavesOtherLinksIntact() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);
    final var otherMr = "urn:uuid:machine-readable-002";
    final var otherHr = "urn:uuid:human-readable-099";
    assetLinkService.createLink(otherMr, otherHr, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);
    assertEquals(4, assetLinkRepository.count());

    assetLinkService.deleteLinksForAsset(HR_IRI);

    final var remaining = assetLinkRepository.findAll();
    assertEquals(2, remaining.size());
    assertTrue(remaining.stream().allMatch(l -> l.getSourceId().equals(otherMr)
        || l.getSourceId().equals(otherHr)));
  }

  @Test
  void deleteLinksForAsset_noLinksExist_doesNotThrow() {
    assetLinkService.deleteLinksForAsset("urn:uuid:no-links");

    assertEquals(0, assetLinkRepository.count());
  }

  // ===== getHumanReadableLinks =====

  @Test
  void getHumanReadableLinks_multipleLinks_returnsForwardRowsOnly() {
    assetLinkService.createLink(MR_IRI, HR_IRI, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);
    final var otherMr = "urn:uuid:machine-readable-003";
    final var otherHr = "urn:uuid:human-readable-003";
    assetLinkService.createLink(otherMr, otherHr, AssetLinkType.HAS_HUMAN_READABLE, CREATOR_DID);

    final var hrLinks = assetLinkService.getHumanReadableLinks();

    assertEquals(2, hrLinks.size());
    assertTrue(hrLinks.stream().allMatch(l -> l.getLinkType() == AssetLinkType.HAS_HUMAN_READABLE));
  }

  @Test
  void getHumanReadableLinks_noLinks_returnsEmptyList() {
    final var hrLinks = assetLinkService.getHumanReadableLinks();

    assertTrue(hrLinks.isEmpty());
  }

  // ===== writeLinkTriples =====

  @Test
  void writeLinkTriples_validIris_doesNotThrow() {
    // With DummyGraphStore, this should silently succeed (no graph interaction)
    assetLinkService.writeLinkTriples(MR_IRI, HR_IRI);
  }

  @Test
  void writeLinkTriples_anglebracketFormat_subjectValueStrippedCorrectly() {
    // CredentialClaim.getSubjectValue() strips <> unconditionally.
    // writeLinkTriples must wrap IRIs in angle brackets before passing to CredentialClaim.
    // Verify indirectly: no exception means the wrapping logic executed without NPE.
    assetLinkService.writeLinkTriples("urn:example:mr", "urn:example:hr");
  }
}
