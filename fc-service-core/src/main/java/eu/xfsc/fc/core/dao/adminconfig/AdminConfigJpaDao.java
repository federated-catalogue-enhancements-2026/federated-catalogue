package eu.xfsc.fc.core.dao.adminconfig;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.dao.AdminConfigDao;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminConfigJpaDao implements AdminConfigDao {

  private final AdminConfigRepository repository;

  @Override
  public Optional<String> getValue(String key) {
    return repository.findById(key)
        .map(AdminConfigEntry::getConfigValue);
  }

  @Override
  @Transactional
  public void setValue(String key, String value) {
    AdminConfigEntry entry = repository.findById(key)
        .orElse(new AdminConfigEntry(key, null, null));
    entry.setConfigValue(value);
    entry.setUpdatedAt(LocalDateTime.now());
    repository.save(entry);
  }

  @Override
  public Map<String, String> getByPrefix(String prefix) {
    return repository.findByConfigKeyStartingWith(prefix).stream()
        .collect(Collectors.toMap(
            AdminConfigEntry::getConfigKey,
            AdminConfigEntry::getConfigValue));
  }
}
