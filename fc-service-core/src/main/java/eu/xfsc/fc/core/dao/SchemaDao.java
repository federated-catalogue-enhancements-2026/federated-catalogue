package eu.xfsc.fc.core.dao;

import java.util.Collection;
import java.util.Map;

import eu.xfsc.fc.core.service.schemastore.SchemaRecord;

public interface SchemaDao {

	int getSchemaCount();
	SchemaRecord select(String schemaId);
	Map<String, Collection<String>> selectSchemas();
	Map<String, Collection<String>> selectSchemasByTerm(String term);
	boolean insert(SchemaRecord schema);
	int update(String id, String content, Collection<String> terms);
	String delete(String schemaId);
	int deleteAll();
	String selectLatestContentByType(String typeName);

}
