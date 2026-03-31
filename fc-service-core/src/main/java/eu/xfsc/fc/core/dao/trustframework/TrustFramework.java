package eu.xfsc.fc.core.dao.trustframework;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trust_frameworks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrustFramework {

  @Id
  @Column(name = "id", length = 255, nullable = false)
  private String id;

  @Column(name = "name", length = 255, nullable = false)
  private String name;

  @Column(name = "service_url", length = 1024)
  private String serviceUrl;

  @Column(name = "api_version", length = 50)
  private String apiVersion;

  @Column(name = "timeout_seconds")
  private int timeoutSeconds;

  @Column(name = "enabled")
  private boolean enabled;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
