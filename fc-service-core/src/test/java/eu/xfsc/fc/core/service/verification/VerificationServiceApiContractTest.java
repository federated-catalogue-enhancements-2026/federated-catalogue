package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;

/**
 * Pins the API contract: all three typed verify methods must return the generic base class.
 * These tests are RED until the typed POJOs are deleted and the method signatures updated.
 */
class VerificationServiceApiContractTest {

  @Test
  void verifyParticipantCredential_returnType_isBaseClass() throws NoSuchMethodException {
    Method m = VerificationService.class.getMethod("verifyParticipantCredential", ContentAccessor.class);
    assertEquals(CredentialVerificationResult.class, m.getReturnType(),
        "verifyParticipantCredential must return CredentialVerificationResult, not a typed subclass");
  }

  @Test
  void verifyOfferingCredential_returnType_isBaseClass() throws NoSuchMethodException {
    Method m = VerificationService.class.getMethod("verifyOfferingCredential", ContentAccessor.class);
    assertEquals(CredentialVerificationResult.class, m.getReturnType(),
        "verifyOfferingCredential must return CredentialVerificationResult, not a typed subclass");
  }

  @Test
  void verifyResourceCredential_returnType_isBaseClass() throws NoSuchMethodException {
    Method m = VerificationService.class.getMethod("verifyResourceCredential", ContentAccessor.class);
    assertEquals(CredentialVerificationResult.class, m.getReturnType(),
        "verifyResourceCredential must return CredentialVerificationResult, not a typed subclass");
  }
}
