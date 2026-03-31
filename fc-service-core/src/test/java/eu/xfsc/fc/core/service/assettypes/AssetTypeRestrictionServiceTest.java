package eu.xfsc.fc.core.service.assettypes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.core.dao.AdminConfigDao;
import eu.xfsc.fc.core.exception.ClientException;

@ExtendWith(MockitoExtension.class)
class AssetTypeRestrictionServiceTest {

  private static final String KEY_ENABLED = "asset.type.restriction.enabled";
  private static final String KEY_ALLOWED = "asset.type.restriction.allowed";

  @Mock
  private AdminConfigDao adminConfigDao;

  @InjectMocks
  private AssetTypeRestrictionService service;

  @Test
  void isRestrictionEnabled_whenTrue_returnsTrue() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("true"));

    assertTrue(service.isRestrictionEnabled());
  }

  @Test
  void isRestrictionEnabled_whenFalse_returnsFalse() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("false"));

    assertFalse(service.isRestrictionEnabled());
  }

  @Test
  void isRestrictionEnabled_whenMissing_returnsFalse() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.empty());

    assertFalse(service.isRestrictionEnabled());
  }

  @Test
  void getAllowedTypes_withJsonArray_returnsParsedList() {
    when(adminConfigDao.getValue(KEY_ALLOWED))
        .thenReturn(Optional.of("[\"VerifiableCredential\",\"ServiceOffering\"]"));

    List<String> result = service.getAllowedTypes();

    assertEquals(2, result.size());
    assertEquals("VerifiableCredential", result.get(0));
    assertEquals("ServiceOffering", result.get(1));
  }

  @Test
  void getAllowedTypes_whenMissing_returnsEmptyList() {
    when(adminConfigDao.getValue(KEY_ALLOWED)).thenReturn(Optional.empty());

    assertTrue(service.getAllowedTypes().isEmpty());
  }

  @Test
  void getAllowedTypes_withMalformedJson_returnsEmptyList() {
    when(adminConfigDao.getValue(KEY_ALLOWED)).thenReturn(Optional.of("not-json"));

    assertTrue(service.getAllowedTypes().isEmpty());
  }

  @Test
  void setConfig_persistsBothKeys() {
    service.setConfig(true, List.of("ServiceOffering"));

    verify(adminConfigDao).setValue(eq(KEY_ENABLED), eq("true"));
    verify(adminConfigDao).setValue(eq(KEY_ALLOWED), eq("[\"ServiceOffering\"]"));
  }

  @Test
  void setConfig_withNullList_persistsEmptyArray() {
    service.setConfig(false, null);

    verify(adminConfigDao).setValue(eq(KEY_ENABLED), eq("false"));
    verify(adminConfigDao).setValue(eq(KEY_ALLOWED), eq("[]"));
  }

  @Test
  void enforceTypeRestriction_whenDisabled_doesNotThrow() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("false"));

    assertDoesNotThrow(() -> service.enforceTypeRestriction(List.of("Anything")));
  }

  @Test
  void enforceTypeRestriction_withMatchingType_doesNotThrow() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("true"));
    when(adminConfigDao.getValue(KEY_ALLOWED))
        .thenReturn(Optional.of("[\"VerifiableCredential\",\"ServiceOffering\"]"));

    assertDoesNotThrow(
        () -> service.enforceTypeRestriction(List.of("VerifiableCredential")));
  }

  @Test
  void enforceTypeRestriction_withNoMatchingType_throwsClientException() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("true"));
    when(adminConfigDao.getValue(KEY_ALLOWED))
        .thenReturn(Optional.of("[\"ServiceOffering\"]"));

    ClientException ex = assertThrows(ClientException.class,
        () -> service.enforceTypeRestriction(List.of("LegalParticipant")));

    assertTrue(ex.getMessage().contains("not allowed"));
    assertTrue(ex.getMessage().contains("ServiceOffering"));
  }

  @Test
  void enforceTypeRestriction_withNullTypes_throwsClientException() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("true"));
    when(adminConfigDao.getValue(KEY_ALLOWED))
        .thenReturn(Optional.of("[\"ServiceOffering\"]"));

    ClientException ex = assertThrows(ClientException.class,
        () -> service.enforceTypeRestriction(null));

    assertTrue(ex.getMessage().contains("no type information"));
  }

  @Test
  void enforceTypeRestriction_withEmptyTypes_throwsClientException() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("true"));
    when(adminConfigDao.getValue(KEY_ALLOWED))
        .thenReturn(Optional.of("[\"ServiceOffering\"]"));

    assertThrows(ClientException.class,
        () -> service.enforceTypeRestriction(Collections.emptyList()));
  }

  @Test
  void enforceTypeRestriction_enabledButNoAllowedTypes_throwsClientException() {
    when(adminConfigDao.getValue(KEY_ENABLED)).thenReturn(Optional.of("true"));
    when(adminConfigDao.getValue(KEY_ALLOWED)).thenReturn(Optional.of("[]"));

    ClientException ex = assertThrows(ClientException.class,
        () -> service.enforceTypeRestriction(List.of("VerifiableCredential")));

    assertTrue(ex.getMessage().contains("no types are configured"));
  }
}
