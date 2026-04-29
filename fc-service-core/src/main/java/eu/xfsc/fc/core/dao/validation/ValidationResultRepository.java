package eu.xfsc.fc.core.dao.validation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
/** Spring Data JPA repository for {@link ValidationResult} entities. */
public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {

  /**
   * Find all validation results where the given asset ID appears in the asset_ids array.
   *
   * <p>Uses the GIN index  (Generalized Inverted Index - designed for composite values) on {@code asset_ids} 
   * for efficient lookup. The {@code = ANY()} operator requires a GIN index on the array column</p>
   */
  @Query(value = "SELECT * FROM validation_result WHERE :assetId = ANY(asset_ids)",
      countQuery = "SELECT COUNT(*) FROM validation_result WHERE :assetId = ANY(asset_ids)",
      nativeQuery = true)
  Page<ValidationResult> findByAssetId(@Param("assetId") String assetId, Pageable pageable);

  /**
   * Mark all validation results for a given asset as outdated with a reason.
   *
   * <p>Uses native query to directly update the DB. Called when an asset is updated or
   * revoked to stale-date its previous validation history.</p>
   */
  @Modifying(clearAutomatically = true)
  @Query(value = "UPDATE validation_result SET outdated = true, outdated_reason = :reason "
      + "WHERE :assetId = ANY(asset_ids)", nativeQuery = true)
  void markOutdatedByAssetId(@Param("assetId") String assetId, @Param("reason") String reason);

  /**
   * Delete all validation results for a given asset.
   *
   * <p>Used during asset deletion to clean up validation history when the asset itself
   * is removed.</p>
   */
  @Modifying
  @Query(value = "DELETE FROM validation_result WHERE :assetId = ANY(asset_ids)",
      nativeQuery = true)
  void deleteByAssetId(@Param("assetId") String assetId);

}
