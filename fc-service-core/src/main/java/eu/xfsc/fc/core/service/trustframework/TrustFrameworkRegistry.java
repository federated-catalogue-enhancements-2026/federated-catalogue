package eu.xfsc.fc.core.service.trustframework;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface TrustFrameworkRegistry {

  ResolvedRole resolveRole(String typeUri);

  Collection<TrustFrameworkBundle> getBundles();

  Optional<TrustFrameworkBundle> getBundle(String profileId);

  Set<String> getEffectiveRoles(String profileId);

  boolean isFrameworkEnabled(String profileId);
}
