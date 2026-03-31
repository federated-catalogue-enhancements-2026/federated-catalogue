package eu.xfsc.fc.core.dao.assets;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;

public final class AssetMapper {

  private AssetMapper() {
  }

  public static AssetRecord toRecord(Asset entity) {
    if (entity == null) {
      return null;
    }
    return AssetRecord.builder()
        .assetHash(entity.getAssetHash())
        .id(entity.getSubjectId())
        .issuer(entity.getIssuer())
        .uploadTime(entity.getUploadTime())
        .statusTime(entity.getStatusTime())
        .expirationTime(entity.getExpirationTime())
        .status(AssetStatus.values()[entity.getStatus()])
        .content(entity.getContent() == null ? null : new ContentAccessorDirect(entity.getContent()))
        .validatorDids(entity.getValidators() == null ? null : Arrays.asList(entity.getValidators()))
        .contentType(entity.getContentType())
        .fileSize(entity.getFileSize())
        .originalFilename(entity.getOriginalFilename())
        .credentialTypes(parseCredentialTypes(entity.getCredentialTypes()))
        .build();
  }

  public static Asset toEntity(AssetRecord record) {
    if (record == null) {
      return null;
    }
    Asset entity = new Asset();
    entity.setAssetHash(record.getAssetHash());
    entity.setSubjectId(record.getId());
    entity.setIssuer(record.getIssuer());
    entity.setUploadTime(record.getUploadDatetime());
    entity.setStatusTime(record.getStatusDatetime());
    entity.setExpirationTime(record.getExpirationTime());
    entity.setStatus((short) record.getStatus().ordinal());
    entity.setContent(record.getContent());
    List<String> dids = record.getValidatorDids();
    entity.setValidators(dids == null ? null : dids.toArray(String[]::new));
    entity.setContentType(record.getContentType());
    entity.setFileSize(record.getFileSize());
    entity.setOriginalFilename(record.getOriginalFilename());
    entity.setCredentialTypes(serializeCredentialTypes(record.getCredentialTypes()));
    return entity;
  }

  private static List<String> parseCredentialTypes(String csv) {
    if (csv == null || csv.isBlank()) {
      return null;
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  private static String serializeCredentialTypes(List<String> types) {
    if (types == null || types.isEmpty()) {
      return null;
    }
    return String.join(",", types);
  }
}
