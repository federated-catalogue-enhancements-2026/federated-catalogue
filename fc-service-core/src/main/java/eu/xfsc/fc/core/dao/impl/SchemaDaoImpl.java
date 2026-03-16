package eu.xfsc.fc.core.dao.impl;

import static java.sql.Types.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.bouncycastle.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.dao.SchemaDao;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SchemaDaoImpl implements SchemaDao {
	
	@Autowired
	private JdbcTemplate jdbc;
	
	@Override
	public int getSchemaCount() {
		String sql = "select count(*) from schemafiles";
		return jdbc.queryForObject(sql, Integer.class);
	}

	@Override
	public SchemaRecord select(String schemaId) {
		String sql = "select schemaId, nameHash, type, uploadTime, updateTime, content from schemafiles where schemaid = ?";
		return jdbc.queryForObject(sql, new Object[] {schemaId}, new int[] {VARCHAR}, new RowMapper<SchemaRecord>() {

			@Override
			public SchemaRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new SchemaRecord(rs.getString(1), rs.getString(2), SchemaType.values()[rs.getInt(3)], rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant(), rs.getString(6), null);
			}
		}); 
	}

	@Override
	public Map<Integer, Collection<String>> selectSchemas() {
	    String sql = "select s.type, s.schemaId from schemafiles s";
	    return jdbc.query(sql, new SchemaAggregateExtractor());
	}
	
	@Override
	public Map<Integer, Collection<String>> selectSchemasByTerm(String term) {
	    String sql = "select s.type, s.schemaId from schemafiles s join schematerms t on t.schemaid = s.schemaid where t.term = ?";
	    return jdbc.query(sql, new Object[] {term}, new int[] {VARCHAR}, new SchemaAggregateExtractor());
	}

	@Override
	public boolean insert(SchemaRecord sr) {
		log.debug("insert.enter; got schema: {}", sr.getId());
		String insertSchemaSql = "insert into schemafiles(schemaId, nameHash, type, uploadTime, updateTime, content) values(?, ?, ?, ?, ?, ?)";
		int rows = jdbc.update(insertSchemaSql, sr.getId(), sr.nameHash(), sr.type().ordinal(),
				Timestamp.from(sr.updateTime()), Timestamp.from(sr.updateTime()), sr.content());
		if (rows > 0 && sr.terms() != null && !sr.terms().isEmpty()) {
			String insertTermsSql = "insert into schematerms(term, schemaid) select * from unnest(?::varchar[], ?::varchar[])";
			String[] schemaIds = new String[sr.terms().size()];
			Arrays.fill(schemaIds, sr.getId());
			String[] terms = sr.terms().toArray(new String[0]);
			jdbc.update(insertTermsSql, (Object) terms, (Object) schemaIds);
		}
		log.debug("insert.exit; inserted: {}", rows);
		return rows > 0;
	}

	@Override
	public int update(String id, String content, Collection<String> terms) {
		log.debug("update.enter; got id: {}, content length: {}, terms: {}", id, content.length(), terms);
		String deleteTermsSql = "delete from schematerms where schemaid = ?";
		int cnt = jdbc.update(deleteTermsSql, id);
		log.debug("update; deleted {} terms", cnt);
		String updateSchemaSql = "update schemafiles set updateTime = ?, content = ? where schemaid = ?";
		int rows = jdbc.update(updateSchemaSql, Timestamp.from(Instant.now()), content, id);
		if (rows > 0 && terms != null && !terms.isEmpty()) {
			String insertTermsSql = "insert into schematerms(term, schemaid) select * from unnest(?::varchar[], ?::varchar[])";
			String[] schemaIds = new String[terms.size()];
			Arrays.fill(schemaIds, id);
			String[] termIds = terms.toArray(new String[0]);
			jdbc.update(insertTermsSql, (Object) termIds, (Object) schemaIds);
		}
		log.debug("update.exit; updated: {}", rows);
		return rows;
	}

	@Override
	public Integer delete(String schemaId) {
		String sql = "delete from schemafiles where schemaid = ? returning type";
		try {
		  return jdbc.queryForObject(sql, new Object[] {schemaId}, new int[] {java.sql.Types.VARCHAR}, Integer.class);
		} catch (EmptyResultDataAccessException ex) {
		  return null;	
		}
	}

	@Override
	public int deleteAll() {
		return jdbc.update("delete from schemafiles");
	}
	
	private class SchemaAggregateExtractor implements ResultSetExtractor<Map<Integer, Collection<String>>> {
    	
    	private Map<Integer, Collection<String>> result = new HashMap<>();
    	
		@Override
		public Map<Integer, Collection<String>> extractData(ResultSet rs) throws SQLException, DataAccessException {
			while (rs.next()) {
				result.computeIfAbsent(rs.getInt(1), t -> new HashSet<>()).add(rs.getString(2));
			}
			return result;
		}
	}

}
