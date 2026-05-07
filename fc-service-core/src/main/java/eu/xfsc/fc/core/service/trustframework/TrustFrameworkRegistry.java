package eu.xfsc.fc.core.service.trustframework;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface TrustFrameworkRegistry {

  ResolvedRole resolveRole(String typeUri);

  Collection<TrustFrameworkBundle> getBundles();

  /**
   * Returns only the bundles that are currently active (i.e. their validation engine is wired and
   * their types participate in role resolution).
   *
   * <p>Deferred bundles — those registered with an unsupported {@code validationType} — are
   * excluded.
   *
   * @return immutable collection of active bundles; never null
   */
  Collection<TrustFrameworkBundle> getActiveBundles();

  Optional<TrustFrameworkBundle> getBundle(String profileId);

  Set<String> getEffectiveRoles(String profileId);

  boolean isFrameworkEnabled(String profileId);

  /**
   * Returns the result-type identifier declared for the given role in the given bundle.
   *
   * @param profileId the bundle profile ID
   * @param role      the role name as declared in the bundle's {@code framework.yaml}
   * @return the declared {@code result_type} string, or empty if the role has none, the role is
   * unknown, or the profile is not registered
   */
  Optional<String> getResultType(String profileId, String role);
}
