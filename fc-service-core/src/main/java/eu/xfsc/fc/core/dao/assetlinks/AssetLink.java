package eu.xfsc.fc.core.dao.assetlinks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * JPA entity for the {@code asset_links} table.
 *
 * <p>Each bidirectional link between assets is stored as two rows:
 * one for each direction (MR → HR and HR → MR). The unique constraint
 * {@code uq_asset_link} prevents duplicate rows.</p>
 */
@Entity
@Table(name = "asset_links")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AssetLink {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_links_seq")
  @SequenceGenerator(name = "asset_links_seq", sequenceName = "asset_links_id_seq")
  @Column(name = "id", nullable = false)
  private Long id;

  /** IRI of the source asset. */
  @Column(name = "source_id", nullable = false, columnDefinition = "TEXT")
  private String sourceId;

  /** IRI of the target asset. */
  @Column(name = "target_id", nullable = false, columnDefinition = "TEXT")
  private String targetId;

  /**
   * Type of link from source to target.
   * Stored as a VARCHAR(50) string — requires {@code @Enumerated(EnumType.STRING)}.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "link_type", nullable = false, length = 50)
  private AssetLinkType linkType;

  /** Timestamp when the link was created. Populated automatically by the auditing listener. */
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** JWT subject of the user who created the link; populated automatically by the auditing listener. */
  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  /** JWT subject of the user who last modified the link; populated automatically by the auditing listener. */
  @LastModifiedBy
  @Column(name = "modified_by")
  private String modifiedBy;

  /** Timestamp of the last modification; populated automatically by the auditing listener. */
  @LastModifiedDate
  @Column(name = "last_modified_at")
  private Instant lastModifiedAt;
}
