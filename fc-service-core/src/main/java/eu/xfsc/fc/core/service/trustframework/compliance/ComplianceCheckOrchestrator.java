package eu.xfsc.fc.core.service.trustframework.compliance;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;

import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Orchestrates a single compliance check: resolves the profile, verifies the family is enabled,
 * dispatches to the appropriate {@link TrustFrameworkClient}, and maps transport failures to
 * {@link UnverifiableAttestation} outcomes.
 *
 * <p>Unknown profile IDs throw {@link ClientException}. Disabled families throw
 * {@link ConflictException}. Network timeouts map to {@link FailureCategory#TIMED_OUT};
 * other I/O failures map to {@link FailureCategory#TRANSPORT_FAILURE}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCheckOrchestrator {

  private final TrustFrameworkRegistry registry;
  private final TrustFrameworkService tfService;
  private final TrustFrameworkClientRegistry clientRegistry;

  /**
   * Runs a compliance check for the given asset against the named trust-framework profile.
   *
   * @param assetId            identifier of the asset being checked
   * @param frameworkProfileId profile to use for the check
   * @param assetPayload       raw credential payload forwarded to the compliance service
   * @return the compliance outcome; never {@code null}
   * @throws ClientException   when {@code frameworkProfileId} is not registered
   * @throws ConflictException when the profile's family is disabled
   */
  public ComplianceCheckOutcome check(String assetId, String frameworkProfileId, String assetPayload) {
    TrustFrameworkProfileConfig config = registry.getProfileConfig(frameworkProfileId)
        .orElseThrow(() -> new ClientException("Unknown trust-framework profile: " + frameworkProfileId));

    if (!tfService.isEnabled(config.familyId())) {
      throw new ConflictException("Trust-framework family is disabled: " + config.familyId());
    }

    TrustFrameworkClient client = clientRegistry.resolve(config.clientType());
    var credential = new ContentAccessorDirect(assetPayload);

    CompletableFuture<ComplianceCheckOutcome> future = CompletableFuture.supplyAsync(
        () -> client.check(credential, config));
    try {
      return future.get(config.timeoutSeconds(), TimeUnit.SECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      return new UnverifiableAttestation(FailureCategory.TIMED_OUT, assetPayload, "Request timed out");
    } catch (ExecutionException e) {
      return mapExecutionException(e.getCause(), assetPayload);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new UnverifiableAttestation(FailureCategory.TRANSPORT_FAILURE, assetPayload, e.getMessage());
    }
  }

  private ComplianceCheckOutcome mapExecutionException(Throwable cause, String assetPayload) {
    if (cause instanceof ResourceAccessException rae && rae.getCause() instanceof SocketTimeoutException) {
      return new UnverifiableAttestation(FailureCategory.TIMED_OUT, assetPayload, cause.getMessage());
    }
    log.error("Compliance check transport failure", cause);
    return new UnverifiableAttestation(FailureCategory.TRANSPORT_FAILURE, assetPayload, cause.getMessage());
  }
}
