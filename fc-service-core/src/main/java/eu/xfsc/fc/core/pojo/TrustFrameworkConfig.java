package eu.xfsc.fc.core.pojo;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for a registered trust framework.
 */
@Getter
@Builder
public class TrustFrameworkConfig {
  private String id;
  private String name;
  private String serviceUrl;
  private String apiVersion;
  private int timeoutSeconds;
  private boolean enabled;
  private Instant createdAt;
  private Instant updatedAt;
}
