package eu.xfsc.fc.core.dao;

import java.util.Map;
import java.util.Optional;

/**
 * Data access interface for key-value admin configuration.
 */
public interface AdminConfigDao {

  /**
   * Get a configuration value by key.
   *
   * @param key configuration key.
   * @return the value, or empty if not found.
   */
  Optional<String> getValue(String key);

  /**
   * Set a configuration value (insert or update).
   *
   * @param key configuration key.
   * @param value configuration value.
   */
  void setValue(String key, String value);

  /**
   * Get all configuration entries matching a key prefix.
   * Uses SQL LIKE internally — prefix must not contain SQL wildcards (%, _).
   *
   * @param prefix key prefix (e.g., "schema.module.").
   * @return map of key-value pairs.
   */
  Map<String, String> getByPrefix(String prefix);
}
