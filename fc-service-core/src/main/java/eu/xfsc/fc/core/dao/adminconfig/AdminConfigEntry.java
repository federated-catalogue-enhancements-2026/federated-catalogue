package eu.xfsc.fc.core.dao.adminconfig;

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
@Table(name = "admin_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminConfigEntry {

  @Id
  @Column(name = "config_key", length = 255, nullable = false)
  private String configKey;

  @Column(name = "config_value", length = 1024)
  private String configValue;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
