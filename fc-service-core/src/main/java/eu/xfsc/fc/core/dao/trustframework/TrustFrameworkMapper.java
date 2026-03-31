package eu.xfsc.fc.core.dao.trustframework;

import java.time.ZoneOffset;

import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

public final class TrustFrameworkMapper {

  private TrustFrameworkMapper() {
  }

  public static TrustFrameworkConfig toConfig(TrustFramework entity) {
    if (entity == null) {
      return null;
    }
    return TrustFrameworkConfig.builder()
        .id(entity.getId())
        .name(entity.getName())
        .serviceUrl(entity.getServiceUrl())
        .apiVersion(entity.getApiVersion())
        .timeoutSeconds(entity.getTimeoutSeconds())
        .enabled(entity.isEnabled())
        .createdAt(entity.getCreatedAt() != null
            ? entity.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
        .updatedAt(entity.getUpdatedAt() != null
            ? entity.getUpdatedAt().toInstant(ZoneOffset.UTC) : null)
        .build();
  }
}
