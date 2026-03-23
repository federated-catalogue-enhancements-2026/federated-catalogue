package eu.xfsc.fc.core.dao.assets;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository
    extends JpaRepository<AssetEntity, String>, AssetRepositoryCustom {

  Optional<AssetEntity> findBySubjectIdAndStatus(String subjectId, short status);

  @Query(value = """
    SELECT asset_hash 
    FROM assets 
    WHERE status = :status AND expirationtime < NOW()
  """, nativeQuery = true)
  List<String> findExpiredHashes(@Param("status") int activeStatus);

  @Query(value = """
    SELECT asset_hash 
    FROM assets 
    WHERE status = :status
        AND abs(hashtext(asset_hash) % :chunks) = :chunkid
    ORDER BY asset_hash ASC LIMIT :limit
  """, nativeQuery = true)
  List<String> findHashesFirstPage(@Param("status") int status,
      @Param("chunks") int chunks, @Param("chunkid") int chunkId,
      @Param("limit") int limit);

  @Query(value = """
    SELECT asset_hash 
    FROM assets 
    WHERE asset_hash > :startHash
        AND status = :status
        AND abs(hashtext(asset_hash) % :chunks) = :chunkid
    ORDER BY asset_hash ASC LIMIT :limit
  """, nativeQuery = true)
  List<String> findHashesAfter(@Param("startHash") String startHash,
      @Param("status") int status, @Param("chunks") int chunks,
      @Param("chunkid") int chunkId, @Param("limit") int limit);
}
