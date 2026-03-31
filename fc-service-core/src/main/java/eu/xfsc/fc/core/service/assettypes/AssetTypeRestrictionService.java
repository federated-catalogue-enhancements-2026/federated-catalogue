package eu.xfsc.fc.core.service.assettypes;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.dao.AdminConfigDao;
import eu.xfsc.fc.core.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages asset type restrictions. When enabled, only assets with
 * allowed types can be uploaded. Types are matched against the
 * credential's JSON-LD @type list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetTypeRestrictionService {

  private static final String KEY_ENABLED = "asset.type.restriction.enabled";
  private static final String KEY_ALLOWED = "asset.type.restriction.allowed";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AdminConfigDao adminConfigDao;

  /**
   * Check if asset type restriction is enabled.
   */
  public boolean isRestrictionEnabled() {
    return adminConfigDao.getValue(KEY_ENABLED)
        .map(Boolean::parseBoolean)
        .orElse(false);
  }

  /**
   * Get the list of allowed asset types.
   */
  public List<String> getAllowedTypes() {
    return adminConfigDao.getValue(KEY_ALLOWED)
        .map(this::parseJsonArray)
        .orElse(Collections.emptyList());
  }

  /**
   * Save the restriction configuration.
   */
  public void setConfig(boolean enabled, List<String> allowedTypes) {
    adminConfigDao.setValue(KEY_ENABLED, String.valueOf(enabled));
    try {
      adminConfigDao.setValue(KEY_ALLOWED, MAPPER.writeValueAsString(allowedTypes != null ? allowedTypes : List.of()));
    } catch (JsonProcessingException e) {
      throw new ClientException("Failed to serialize allowed types: " + e.getMessage());
    }
  }

  /**
   * Enforce type restriction on a credential's types.
   * Throws ClientException (HTTP 400) if restriction is enabled and
   * the credential has no type in the allowed list.
   *
   * @param credentialTypes the JSON-LD @type list from the credential.
   */
  public void enforceTypeRestriction(List<String> credentialTypes) {
    if (!isRestrictionEnabled()) {
      return;
    }
    List<String> allowed = getAllowedTypes();
    if (allowed.isEmpty()) {
      throw new ClientException("Asset type restriction is enabled but no types are configured. "
          + "All uploads are blocked. Configure allowed types in the admin UI.");
    }
    if (credentialTypes == null || credentialTypes.isEmpty()) {
      throw new ClientException("Asset has no type information. "
          + "Allowed types: " + String.join(", ", allowed));
    }
    boolean hasAllowedType = credentialTypes.stream().anyMatch(allowed::contains);
    if (!hasAllowedType) {
      throw new ClientException("Asset type not allowed. "
          + "Found: " + String.join(", ", credentialTypes) + ". "
          + "Allowed: " + String.join(", ", allowed));
    }
  }

  private List<String> parseJsonArray(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse allowed types JSON: {}", json, e);
      return Collections.emptyList();
    }
  }
}
