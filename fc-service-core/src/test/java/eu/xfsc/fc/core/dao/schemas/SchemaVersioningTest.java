package eu.xfsc.fc.core.dao.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Tests for schema versioning via Envers audit infrastructure.
 *
 * <p>Uses {@link TransactionTemplate} with explicit commits so Envers
 * audit entries are written (class-level @Transactional would roll back
 * and produce zero revisions).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SchemaVersioningTest.TestConfig.class, SchemaJpaDao.class, DatabaseConfig.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class SchemaVersioningTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private SchemaDao schemaDao;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void cleanUp() {
    transactionTemplate.executeWithoutResult(status -> schemaDao.deleteAll());
  }

  // ===== selectVersions =====

  @Test
  void selectVersions_afterInsert_returnsVersion1() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "original content", Set.of("termA"))));

    List<SchemaRecord> versions = schemaDao.selectVersions("s-1");

    assertEquals(1, versions.size());
    SchemaRecord v1 = versions.getFirst();
    assertEquals(1, v1.version());
    assertEquals("original content", v1.content());
    assertNotNull(v1.createdAt());
  }

  @Test
  void selectVersions_afterUpdate_returnsVersions1And2() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "original", Set.of("termA"))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "updated", Set.of("termB")));

    List<SchemaRecord> versions = schemaDao.selectVersions("s-1");

    assertEquals(2, versions.size());
    assertEquals(1, versions.get(0).version());
    assertEquals("original", versions.get(0).content());
    assertEquals(2, versions.get(1).version());
    assertEquals("updated", versions.get(1).content());
  }

  @Test
  void selectVersions_termsPreservedPerVersion() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "content-v1", Set.of("termA", "termB"))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "content-v2", Set.of("termC")));

    List<SchemaRecord> versions = schemaDao.selectVersions("s-1");
    assertEquals(Set.of("termA", "termB"), versions.get(0).terms());
    assertEquals(Set.of("termC"), versions.get(1).terms());
  }

  @Test
  void selectVersions_nonExistentSchema_returnsEmptyList() {
    List<SchemaRecord> versions = schemaDao.selectVersions("nonexistent");

    assertTrue(versions.isEmpty());
  }

  @Test
  void selectVersions_threeUpdates_producesVersions123() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "v1", null)));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "v2", null));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "v3", null));

    List<SchemaRecord> versions = schemaDao.selectVersions("s-1");

    assertEquals(3, versions.size());
    assertEquals(1, versions.get(0).version());
    assertEquals("v1", versions.get(0).content());
    assertEquals(2, versions.get(1).version());
    assertEquals("v2", versions.get(1).content());
    assertEquals(3, versions.get(2).version());
    assertEquals("v3", versions.get(2).content());
  }

  @Test
  void selectVersions_ascendingOrder() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "v1", null)));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "v2", null));

    List<SchemaRecord> versions = schemaDao.selectVersions("s-1");

    assertTrue(versions.get(0).createdAt().isBefore(versions.get(1).createdAt())
            || versions.get(0).createdAt().equals(versions.get(1).createdAt()),
        "Versions should be in ascending order");
  }

  // ===== selectVersion =====

  @Test
  void selectVersion_version1_returnsOriginalContent() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "original", Set.of("termA"))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "updated", Set.of("termB")));

    Optional<SchemaRecord> v1 = schemaDao.selectVersion("s-1", 1);

    assertTrue(v1.isPresent());
    assertEquals("original", v1.get().content());
    assertEquals(1, v1.get().version());
    assertEquals(Set.of("termA"), v1.get().terms());
  }

  @Test
  void selectVersion_version2_returnsUpdatedContent() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "original", Set.of("termA"))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("s-1", "updated", Set.of("termB")));

    Optional<SchemaRecord> v2 = schemaDao.selectVersion("s-1", 2);

    assertTrue(v2.isPresent());
    assertEquals("updated", v2.get().content());
    assertEquals(2, v2.get().version());
    assertEquals(Set.of("termB"), v2.get().terms());
  }

  @Test
  void selectVersion_nonExistentVersion_returnsEmpty() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "content", null)));

    Optional<SchemaRecord> result = schemaDao.selectVersion("s-1", 999);

    assertTrue(result.isEmpty());
  }

  @Test
  void selectVersion_versionZero_returnsEmpty() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "content", null)));

    Optional<SchemaRecord> result = schemaDao.selectVersion("s-1", 0);

    assertTrue(result.isEmpty());
  }

  @Test
  void selectVersion_negativeVersion_returnsEmpty() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("s-1", "h-1", SchemaType.ONTOLOGY,
            "content", null)));

    Optional<SchemaRecord> result = schemaDao.selectVersion("s-1", -1);

    assertTrue(result.isEmpty());
  }
}
