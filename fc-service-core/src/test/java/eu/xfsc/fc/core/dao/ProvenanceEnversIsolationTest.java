package eu.xfsc.fc.core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.dao.assets.Asset;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.dao.provenance.ProvenanceCredentialRepository;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies that saving a {@link ProvenanceRecord} does not create a new Envers
 * revision on the parent {@link Asset} entity. The {@code provenance_credentials}
 * table is intentionally not annotated with {@code @Audited}, so provenance writes
 * must remain invisible to Hibernate Envers.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    ProvenanceEnversIsolationTest.TestConfig.class,
    AssetJpaDao.class, AssetAuditRepository.class,
    ProvenanceCredentialRepository.class,
    DatabaseConfig.class, SecurityAuditorAware.class
})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class ProvenanceEnversIsolationTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private AssetDao assetDao;

  @Autowired
  private ProvenanceCredentialRepository provenanceRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanUp() {
    transactionTemplate.executeWithoutResult(status ->
        jdbcTemplate.execute(
            "TRUNCATE provenance_credentials, assets_aud, revinfo, assets CASCADE"));
  }

  @Test
  void addProvenanceCredential_doesNotCreateNewAssetRevision() {
    final String subjectId = "prov/isolation/1";
    final String assetHash = "hash-prov-iso";

    // Arrange: insert asset → creates exactly 1 Envers revision
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(AssetRecord.builder()
            .assetHash(assetHash)
            .id(subjectId)
            .issuer("did:issuer:1")
            .uploadTime(Instant.parse("2024-01-01T00:00:00Z"))
            .statusTime(Instant.parse("2024-01-01T00:00:00Z"))
            .expirationTime(null)
            .status(AssetStatus.ACTIVE)
            .content(new ContentAccessorDirect("content"))
            .validatorDids(List.of("did:val:1"))
            .contentType("application/ld+json")
            .fileSize(100L)
            .originalFilename("file.jsonld")
            .build())
    );

    List<Number> revisionsBefore = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      List<?> results = reader.createQuery()
          .forRevisionsOfEntity(Asset.class, true, true)
          .getResultList();
      Long entityId = ((Asset) results.getFirst()).getId();
      return reader.getRevisions(Asset.class, entityId);
    });

    assertNotNull(revisionsBefore);
    assertEquals(1, revisionsBefore.size(), "Expected exactly 1 Envers revision after asset insert");

    // Act: save a provenance credential in a separate transaction (mimics REQUIRES_NEW)
    transactionTemplate.executeWithoutResult(status ->
        provenanceRepository.save(ProvenanceRecord.builder()
            .assetId(subjectId)
            .assetVersion(1)
            .credentialId("did:vc:prov-isolation-1")
            .issuer("did:issuer:1")
            .issuedAt(Instant.parse("2024-06-01T00:00:00Z"))
            .provenanceType(ProvenanceType.CREATION)
            .credentialContent("{}")
            .credentialFormat("application/ld+json")
            .verified(false)
            .build())
    );

    // Assert: Envers revision count on the Asset entity is unchanged
    List<Number> revisionsAfter = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      List<?> results = reader.createQuery()
          .forRevisionsOfEntity(Asset.class, true, true)
          .getResultList();
      Long entityId = ((Asset) results.getFirst()).getId();
      return reader.getRevisions(Asset.class, entityId);
    });

    assertNotNull(revisionsAfter);
    assertEquals(revisionsBefore.size(), revisionsAfter.size(),
        "Provenance credential save must not create a new Envers revision on the Asset");
  }
}
