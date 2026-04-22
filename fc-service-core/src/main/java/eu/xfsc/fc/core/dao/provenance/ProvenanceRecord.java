package eu.xfsc.fc.core.dao.provenance;

import eu.xfsc.fc.core.service.provenance.ProvenanceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * JPA entity representing an append-only provenance credential attached to a specific
 * version of an asset. Stored in the {@code provenance_credentials} table.
 *
 * <p>This entity is intentionally NOT annotated with {@code @Audited} to ensure that
 * provenance writes do not create new Envers revisions on the parent {@code Asset} entity.</p>
 */
@Entity
@Table(name = "provenance_credentials")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvenanceRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  /**
   * Logical FK to {@code assets.subjectid}. Not a database-level FK to avoid coupling.
   */
  @Column(name = "asset_id", length = 500, nullable = false)
  private String assetId;

  /**
   * 1-based Envers revision ordinal the credential is linked to.
   */
  @Column(name = "asset_version", nullable = false)
  private int assetVersion;

  /**
   * VC {@code id} field. Unique per credential.
   */
  @Column(name = "credential_id", length = 500, unique = true)
  private String credentialId;

  @Column(name = "issuer", length = 500)
  private String issuer;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "provenance_type", length = 32)
  private ProvenanceType provenanceType;

  @Column(name = "credential_content", columnDefinition = "TEXT")
  private String credentialContent;

  @Column(name = "credential_format", length = 20)
  private String credentialFormat;

  @Column(name = "verified", nullable = false)
  @Builder.Default
  private boolean verified = false;

  @Column(name = "verification_timestamp")
  private Instant verificationTimestamp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "verification_result", columnDefinition = "JSONB")
  private String verificationResult;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "last_modified_at")
  private Instant lastModifiedAt;
}
