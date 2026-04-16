package eu.xfsc.fc.core.dao.assetlinks;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link AssetLink} entities.
 *
 * <p>Provides CRUD and custom query operations against the {@code asset_links} table.
 * PostgreSQL is the source of truth for all link data.</p>
 */
public interface AssetLinkRepository extends JpaRepository<AssetLink, Long> {

  /**
   * Find all links where the given IRI is the source.
   *
   * @param sourceId source asset IRI
   * @return list of matching links, empty if none
   */
  List<AssetLink> findBySourceId(String sourceId);

  /**
   * Find all links where the given IRI is the target.
   *
   * @param targetId target asset IRI
   * @return list of matching links, empty if none
   */
  List<AssetLink> findByTargetId(String targetId);

  /**
   * Find a single link for the given source IRI and link type.
   * Used to resolve the linked asset and to check for existing links before creating.
   *
   * @param sourceId source asset IRI
   * @param linkType the direction of the link
   * @return the link, or empty if not found
   */
  Optional<AssetLink> findBySourceIdAndLinkType(String sourceId, AssetLinkType linkType);

  /**
   * Find all links of the given type.
   * Used by graph rebuild to restore link triples without loading all link rows into memory.
   *
   * @param linkType the link direction to filter by
   * @return list of matching links, empty if none
   */
  List<AssetLink> findByLinkType(AssetLinkType linkType);

  /**
   * Delete all rows where the IRI appears as either source or target.
   * Used during asset deletion to clean up both directions of a bidirectional link.
   *
   * @param sourceId IRI to match as source
   * @param targetId IRI to match as target (pass the same value to remove all rows for one asset)
   */
  @Transactional
  void deleteBySourceIdOrTargetId(String sourceId, String targetId);
}
