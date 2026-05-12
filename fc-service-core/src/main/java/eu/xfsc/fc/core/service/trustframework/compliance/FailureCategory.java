package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Classifies the reason a compliance check could not produce a positive outcome.
 */
public enum FailureCategory {
  TIMED_OUT,
  TRANSPORT_FAILURE,
  UNVERIFIABLE_ATTESTATION
}
