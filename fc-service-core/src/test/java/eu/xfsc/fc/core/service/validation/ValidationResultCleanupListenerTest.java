package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import eu.xfsc.fc.core.service.assetstore.AssetDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationResultCleanupListenerTest {

  private ValidationResultStore validationResultStore;
  private ValidationResultCleanupListener listener;

  @BeforeEach
  void setUp() {
    validationResultStore = mock(ValidationResultStore.class);
    listener = new ValidationResultCleanupListener(validationResultStore);
  }

  @Test
  void onAssetDeleted_validEvent_callsDeleteByAssetId() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-1");

    listener.onAssetDeleted(event);

    verify(validationResultStore).deleteByAssetId("https://example.org/asset/deleted-1");
  }

  @Test
  void onAssetDeleted_storeThrows_propagatesException() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-2");
    doThrow(new RuntimeException("db unavailable"))
        .when(validationResultStore).deleteByAssetId("https://example.org/asset/deleted-2");

    assertThrows(RuntimeException.class, () -> listener.onAssetDeleted(event));
  }
}
