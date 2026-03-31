package eu.xfsc.fc.core.dao;

import java.util.List;
import java.util.Optional;

import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

/**
 * Data access interface for trust framework configuration.
 */
public interface TrustFrameworkDao {

  /**
   * Get all registered trust frameworks.
   *
   * @return list of all trust frameworks.
   */
  List<TrustFrameworkConfig> findAll();

  /**
   * Find a trust framework by its ID.
   *
   * @param id framework identifier.
   * @return the framework config, or empty if not found.
   */
  Optional<TrustFrameworkConfig> findById(String id);

  /**
   * Toggle a trust framework's enabled state.
   *
   * @param id framework identifier.
   * @param enabled new enabled state.
   * @return number of rows updated (0 if not found).
   */
  int updateEnabled(String id, boolean enabled);

  /**
   * Update a trust framework's connection configuration.
   *
   * @param id framework identifier.
   * @param serviceUrl new service URL.
   * @param apiVersion new API version.
   * @param timeoutSeconds new timeout in seconds.
   * @return number of rows updated (0 if not found).
   */
  int updateConfig(String id, String serviceUrl, String apiVersion, int timeoutSeconds);

  /**
   * Count frameworks that are enabled.
   *
   * @return number of enabled trust frameworks.
   */
  long countEnabled();
}
