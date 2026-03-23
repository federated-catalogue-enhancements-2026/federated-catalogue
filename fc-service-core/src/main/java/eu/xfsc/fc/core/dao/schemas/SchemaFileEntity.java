package eu.xfsc.fc.core.dao.schemas;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schemafiles")
@Getter
@Setter
@NoArgsConstructor
public class SchemaFileEntity implements Persistable<String> {

  @Id
  @Column(name = "schemaid", length = 200)
  private String schemaId;

  @Column(name = "namehash", length = 64, nullable = false)
  private String nameHash;

  @Column(name = "uploadtime", nullable = false)
  private Instant uploadTime;

  @Column(name = "updatetime", nullable = false)
  private Instant updateTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 20, nullable = false)
  private SchemaType type;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @OneToMany(mappedBy = "schemaFile", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<SchemaTermEntity> terms = new HashSet<>();

  @Transient
  @Getter(lombok.AccessLevel.NONE)
  @Setter(lombok.AccessLevel.NONE)
  private boolean newEntity = true;

  @Override
  public String getId() {
    return schemaId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.newEntity = false;
  }
}
