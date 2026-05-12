package eu.xfsc.fc.core.service.trustframework.compliance;

import java.time.Instant;

/**
 * Outcome indicating that the asset is listed as a member in a published trusted list.
 *
 * @param trustListUri        URI of the trust list that contains the member entry
 * @param trustListSignerDid  DID of the entity that signed the trust list
 * @param trustListNextUpdate timestamp when the trust list is expected to be refreshed
 * @param memberEntry         the raw entry from the trust list identifying the member
 */
public record TrustListMembership(
    String trustListUri,
    String trustListSignerDid,
    Instant trustListNextUpdate,
    String memberEntry
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return true;
  }
}
