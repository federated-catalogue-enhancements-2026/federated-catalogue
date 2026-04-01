package eu.xfsc.fc.core.dao.assets;

import java.util.List;
import java.util.Optional;

import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectStatusRecord;

public interface AssetDao {

	AssetRecord select(String hash);
	Optional<AssetRecord> selectBySubjectId(String subjectId);
    PaginatedResults<AssetRecord> selectByFilter(AssetFilter filter, boolean withMeta, boolean withContent);
	List<String> selectHashes(String startHash, int count, int chunks, int chunkId);
	List<String> selectExpiredHashes();
	SubjectHashRecord insert(AssetRecord assetRecord);
	SubjectStatusRecord update(String hash, int status);
	SubjectStatusRecord delete(String hash);
	int deleteAll();

	/** Returns all versions descending (newest first). Empty list if asset not found. */
	List<AssetRecord> selectVersions(String subjectId);

	/** Returns paginated versions + total count in one DAO call (avoids double findBySubjectId). */
	PaginatedResults<AssetRecord> selectVersionsPageWithTotal(String subjectId, int page, int size);

	/** Returns a specific version by 1-based ordinal, or empty if not found. */
	Optional<AssetRecord> selectVersion(String subjectId, int version);

	/** Returns the total number of Envers revisions for the asset. 0 if not found. */
	int getVersionCount(String subjectId);

}
