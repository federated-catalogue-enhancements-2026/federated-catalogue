package eu.xfsc.fc.core.dao.trustframework;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.dao.TrustFrameworkDao;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TrustFrameworkJpaDao implements TrustFrameworkDao {

  private final TrustFrameworkRepository repository;

  @Override
  public List<TrustFrameworkConfig> findAll() {
    return repository.findAll().stream()
        .map(TrustFrameworkMapper::toConfig)
        .toList();
  }

  @Override
  public Optional<TrustFrameworkConfig> findById(String id) {
    return repository.findById(id)
        .map(TrustFrameworkMapper::toConfig);
  }

  @Override
  @Transactional
  public int updateEnabled(String id, boolean enabled) {
    return repository.findById(id)
        .map(entity -> {
          entity.setEnabled(enabled);
          entity.setUpdatedAt(LocalDateTime.now());
          repository.save(entity);
          return 1;
        })
        .orElse(0);
  }

  @Override
  @Transactional
  public int updateConfig(String id, String serviceUrl, String apiVersion, int timeoutSeconds) {
    return repository.findById(id)
        .map(entity -> {
          entity.setServiceUrl(serviceUrl);
          entity.setApiVersion(apiVersion);
          entity.setTimeoutSeconds(timeoutSeconds);
          entity.setUpdatedAt(LocalDateTime.now());
          repository.save(entity);
          return 1;
        })
        .orElse(0);
  }

  @Override
  public long countEnabled() {
    return repository.countByEnabledTrue();
  }
}
