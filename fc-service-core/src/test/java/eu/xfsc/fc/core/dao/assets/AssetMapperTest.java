package eu.xfsc.fc.core.dao.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;

/**
 * Unit tests for AssetMapper credential types mapping.
 */
class AssetMapperTest {

  private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

  // ===== toEntity: credentialTypes serialization =====

  @Test
  void toEntity_withCredentialTypes_serializesAsCsv() {
    AssetRecord record = AssetRecord.builder()
        .assetHash("hash1").id("sub/1").status(AssetStatus.ACTIVE)
        .uploadTime(NOW).statusTime(NOW)
        .credentialTypes(List.of("VerifiablePresentation", "ServiceOffering"))
        .build();

    Asset entity = AssetMapper.toEntity(record);

    assertEquals("VerifiablePresentation,ServiceOffering", entity.getCredentialTypes());
  }

  @Test
  void toEntity_withNullCredentialTypes_serializesAsNull() {
    AssetRecord record = AssetRecord.builder()
        .assetHash("hash2").id("sub/2").status(AssetStatus.ACTIVE)
        .uploadTime(NOW).statusTime(NOW)
        .credentialTypes(null)
        .build();

    Asset entity = AssetMapper.toEntity(record);

    assertNull(entity.getCredentialTypes());
  }

  @Test
  void toEntity_withEmptyCredentialTypes_serializesAsNull() {
    AssetRecord record = AssetRecord.builder()
        .assetHash("hash3").id("sub/3").status(AssetStatus.ACTIVE)
        .uploadTime(NOW).statusTime(NOW)
        .credentialTypes(List.of())
        .build();

    Asset entity = AssetMapper.toEntity(record);

    assertNull(entity.getCredentialTypes());
  }

  @Test
  void toEntity_nullRecord_returnsNull() {
    assertNull(AssetMapper.toEntity(null));
  }

  // ===== toRecord: credentialTypes parsing =====

  @Test
  void toRecord_withCsvCredentialTypes_parsesToList() {
    Asset entity = new Asset();
    entity.setAssetHash("hash4");
    entity.setSubjectId("sub/4");
    entity.setStatus((short) AssetStatus.ACTIVE.ordinal());
    entity.setUploadTime(NOW);
    entity.setStatusTime(NOW);
    entity.setCredentialTypes("VerifiablePresentation,ServiceOffering");

    AssetRecord record = AssetMapper.toRecord(entity);

    assertEquals(2, record.getCredentialTypes().size());
    assertEquals("VerifiablePresentation", record.getCredentialTypes().get(0));
    assertEquals("ServiceOffering", record.getCredentialTypes().get(1));
  }

  @Test
  void toRecord_withWhitespaceCsv_trimsValues() {
    Asset entity = new Asset();
    entity.setAssetHash("hash5");
    entity.setSubjectId("sub/5");
    entity.setStatus((short) AssetStatus.ACTIVE.ordinal());
    entity.setUploadTime(NOW);
    entity.setStatusTime(NOW);
    entity.setCredentialTypes(" VP , SC , LegalParticipant ");

    AssetRecord record = AssetMapper.toRecord(entity);

    assertEquals(3, record.getCredentialTypes().size());
    assertEquals("VP", record.getCredentialTypes().get(0));
    assertEquals("SC", record.getCredentialTypes().get(1));
    assertEquals("LegalParticipant", record.getCredentialTypes().get(2));
  }

  @Test
  void toRecord_withNullCredentialTypes_returnsNull() {
    Asset entity = new Asset();
    entity.setAssetHash("hash6");
    entity.setSubjectId("sub/6");
    entity.setStatus((short) AssetStatus.ACTIVE.ordinal());
    entity.setUploadTime(NOW);
    entity.setStatusTime(NOW);
    entity.setCredentialTypes(null);

    AssetRecord record = AssetMapper.toRecord(entity);

    assertNull(record.getCredentialTypes());
  }

  @Test
  void toRecord_withBlankCredentialTypes_returnsNull() {
    Asset entity = new Asset();
    entity.setAssetHash("hash7");
    entity.setSubjectId("sub/7");
    entity.setStatus((short) AssetStatus.ACTIVE.ordinal());
    entity.setUploadTime(NOW);
    entity.setStatusTime(NOW);
    entity.setCredentialTypes("   ");

    AssetRecord record = AssetMapper.toRecord(entity);

    assertNull(record.getCredentialTypes());
  }

  @Test
  void toRecord_withEmptyStringCredentialTypes_returnsNull() {
    Asset entity = new Asset();
    entity.setAssetHash("hash8");
    entity.setSubjectId("sub/8");
    entity.setStatus((short) AssetStatus.ACTIVE.ordinal());
    entity.setUploadTime(NOW);
    entity.setStatusTime(NOW);
    entity.setCredentialTypes("");

    AssetRecord record = AssetMapper.toRecord(entity);

    assertNull(record.getCredentialTypes());
  }

  @Test
  void toRecord_nullEntity_returnsNull() {
    assertNull(AssetMapper.toRecord(null));
  }

  // ===== Round-trip =====

  @Test
  void roundTrip_credentialTypes_preservedExactly() {
    List<String> originalTypes = List.of("VerifiablePresentation", "ServiceOffering", "LegalParticipant");

    AssetRecord record = AssetRecord.builder()
        .assetHash("hash-rt").id("sub/rt").status(AssetStatus.ACTIVE)
        .uploadTime(NOW).statusTime(NOW)
        .content(new ContentAccessorDirect("content"))
        .credentialTypes(originalTypes)
        .build();

    Asset entity = AssetMapper.toEntity(record);
    AssetRecord roundTripped = AssetMapper.toRecord(entity);

    assertEquals(originalTypes, roundTripped.getCredentialTypes());
  }
}
