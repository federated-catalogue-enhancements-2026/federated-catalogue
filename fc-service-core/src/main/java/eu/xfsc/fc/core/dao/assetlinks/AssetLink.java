package eu.xfsc.fc.core.dao.assetlinks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * JPA entity for the {@code asset_links} table.
 *
 * <p>Each bidirectional link between assets is stored as two rows:
 * one for each direction (MR → HR and HR → MR). The unique constraint
 * {@code uq_asset_link} prevents duplicate rows.</p>
 */
@Entity
@Table(name = "asset_links")
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

  /** Timestamp when the link was created. Populated automatically on insert. */
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** DID of the user who created the link; may be null for system-generated links. */
  @Column(name = "created_by", columnDefinition = "TEXT")
  private String createdBy;
}
