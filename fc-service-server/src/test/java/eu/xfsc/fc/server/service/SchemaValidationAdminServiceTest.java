package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.OntologyImpactService;
import eu.xfsc.fc.core.service.verification.RevalidationService;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;

/**
 * Unit tests for {@link SchemaValidationAdminService} — specifically the SHACL re-enable
 * wiring that triggers a full background revalidation sweep so chunks skipped while SHACL
 * was off are not left behind. The chunk-pickup model is documented in
 * {@link eu.xfsc.fc.core.service.verification.RevalidationServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchemaValidationAdminServiceTest {

  private static final String SHACL_KEY = "schema.module.SHACL.enabled";

  @Mock
  private AdminConfigRepository adminConfigRepository;

  @Mock
  private SchemaStore schemaStore;

  @Mock
  private OntologyImpactService ontologyImpactService;

  @Mock
  private RevalidationService revalidationService;

  @Mock
  private ObjectProvider<RevalidationService> revalidationServiceProvider;

  @InjectMocks
  private SchemaValidationAdminService service;

  @Test
  void setSchemaModuleEnabled_shaclFalseToTrue_triggersRevalidationSweep() {
    AdminConfigEntry previousOff = new AdminConfigEntry(SHACL_KEY, "false", null);
    when(adminConfigRepository.findById(SHACL_KEY)).thenReturn(Optional.of(previousOff));
    when(revalidationServiceProvider.getIfAvailable()).thenReturn(revalidationService);

    ResponseEntity<Void> response = service.setSchemaModuleEnabled(SchemaModuleType.SHACL, Boolean.TRUE);

    assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
    verify(revalidationService, times(1)).startValidating();
  }

  @Test
  void setSchemaModuleEnabled_shaclStaysOn_doesNotTriggerRevalidationSweep() {
    AdminConfigEntry previouslyOn = new AdminConfigEntry(SHACL_KEY, "true", null);
    when(adminConfigRepository.findById(SHACL_KEY)).thenReturn(Optional.of(previouslyOn));

    service.setSchemaModuleEnabled(SchemaModuleType.SHACL, Boolean.TRUE);

    verify(revalidationServiceProvider, never()).getIfAvailable();
    verify(revalidationService, never()).startValidating();
  }

  @Test
  void setSchemaModuleEnabled_shaclEnableToDisable_doesNotTriggerRevalidationSweep() {
    // No prior entry → default state is "enabled" per SchemaModuleConfigService semantics.
    // Setting it to false now is an enable→disable transition, not a re-enable.
    when(adminConfigRepository.findById(SHACL_KEY)).thenReturn(Optional.empty());

    service.setSchemaModuleEnabled(SchemaModuleType.SHACL, Boolean.FALSE);

    verify(revalidationServiceProvider, never()).getIfAvailable();
    verify(revalidationService, never()).startValidating();
  }

  @Test
  void setSchemaModuleEnabled_owlFalseToTrue_doesNotTriggerRevalidationSweep() {
    // The sweep gate is SHACL-specific. Toggling other modules must not call
    // startValidating().
    AdminConfigEntry previousOff = new AdminConfigEntry("schema.module.OWL.enabled", "false", null);
    when(adminConfigRepository.findById("schema.module.OWL.enabled")).thenReturn(Optional.of(previousOff));

    service.setSchemaModuleEnabled(SchemaModuleType.OWL, Boolean.TRUE);

    verify(revalidationServiceProvider, never()).getIfAvailable();
    verify(revalidationService, never()).startValidating();
  }

  @Test
  void setSchemaModuleEnabled_shaclReEnabledButNoRevalidationBean_logsAndReturnsOk() {
    AdminConfigEntry previousOff = new AdminConfigEntry(SHACL_KEY, "false", null);
    when(adminConfigRepository.findById(SHACL_KEY)).thenReturn(Optional.of(previousOff));
    when(revalidationServiceProvider.getIfAvailable()).thenReturn(null);

    ResponseEntity<Void> response = service.setSchemaModuleEnabled(SchemaModuleType.SHACL, Boolean.TRUE);

    assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
    verify(revalidationService, never()).startValidating();
    verify(adminConfigRepository, times(1)).save(any());
  }
}
