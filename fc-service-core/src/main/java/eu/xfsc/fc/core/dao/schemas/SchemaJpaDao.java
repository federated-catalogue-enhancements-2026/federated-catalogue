package eu.xfsc.fc.core.dao.schemas;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.dao.SchemaDao;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SchemaJpaDao implements SchemaDao {

  private final SchemaFileRepository repository;

  @Override
  public int getSchemaCount() {
    return (int) repository.count();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SchemaRecord> select(String schemaId) {
    return repository.findById(schemaId)
        .map(SchemaFileEntityMapper::toRecord);
  }

  @Override
  public Map<String, Collection<String>> selectSchemas() {
    return aggregateToMap(repository.findAllTypeAndSchemaId());
  }

  @Override
  public Map<String, Collection<String>> selectSchemasByTerm(String term) {
    return aggregateToMap(repository.findTypeAndSchemaIdByTerm(term));
  }

  @Override
  public boolean insert(SchemaRecord sr) {
    repository.saveAndFlush(SchemaFileEntityMapper.toEntity(sr));
    return true;
  }

  @Override
  @Transactional
  public int update(String id, String content, Collection<String> terms) {
    return repository.findById(id).map(entity -> {
      entity.setUpdateTime(Instant.now());
      entity.setContent(content);
      entity.getTerms().clear();
      if (terms != null) {
        for (String term : terms) {
          SchemaTermEntity te = new SchemaTermEntity();
          te.setTerm(term);
          te.setSchemaFile(entity);
          entity.getTerms().add(te);
        }
      }
      repository.saveAndFlush(entity);
      return 1;
    }).orElse(0);
  }

  @Override
  @Transactional
  public String delete(String schemaId) {
    return repository.findById(schemaId).map(entity -> {
      String type = entity.getType().name();
      repository.delete(entity);
      return type;
    }).orElse(null);
  }

  @Override
  public int deleteAll() {
    int count = (int) repository.count();
    repository.deleteAllInBatch();
    return count;
  }

  @Override
  public Optional<String> selectLatestContentByType(String typeName) {
    return repository.findLatestContentByType(SchemaType.valueOf(typeName));
  }

  private Map<String, Collection<String>> aggregateToMap(List<Object[]> rows) {
    Map<String, Collection<String>> result = new HashMap<>();
    for (Object[] row : rows) {
      String type = ((SchemaType) row[0]).name();
      String schemaId = (String) row[1];
      result.computeIfAbsent(type, t -> new HashSet<>()).add(schemaId);
    }
    return result;
  }
}
