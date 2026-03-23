package eu.xfsc.fc.core.dao.schemas;

import java.util.Set;
import java.util.stream.Collectors;

import eu.xfsc.fc.core.service.schemastore.SchemaRecord;

public final class SchemaFileEntityMapper {

  private SchemaFileEntityMapper() {
  }

  public static SchemaRecord toRecord(SchemaFileEntity entity) {
    if (entity == null) {
      return null;
    }
    Set<String> terms = entity.getTerms() == null ? null
        : entity.getTerms().stream()
            .map(SchemaTermEntity::getTerm)
            .collect(Collectors.toSet());
    return new SchemaRecord(
        entity.getSchemaId(),
        entity.getNameHash(),
        entity.getType(),
        entity.getUploadTime(),
        entity.getUpdateTime(),
        entity.getContent(),
        terms);
  }

  public static SchemaFileEntity toEntity(SchemaRecord record) {
    if (record == null) {
      return null;
    }
    SchemaFileEntity entity = new SchemaFileEntity();
    entity.setSchemaId(record.getId());
    entity.setNameHash(record.nameHash());
    entity.setType(record.type());
    entity.setUploadTime(record.uploadTime());
    entity.setUpdateTime(record.updateTime());
    entity.setContent(record.content());
    if (record.terms() != null) {
      Set<SchemaTermEntity> termEntities = record.terms().stream().map(term -> {
        SchemaTermEntity te = new SchemaTermEntity();
        te.setTerm(term);
        te.setSchemaFile(entity);
        return te;
      }).collect(Collectors.toSet());
      entity.setTerms(termEntities);
    }
    return entity;
  }
}
