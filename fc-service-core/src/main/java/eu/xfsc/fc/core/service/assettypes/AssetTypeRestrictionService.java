package eu.xfsc.fc.core.service.assettypes;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  private static final int MAX_TYPE_LENGTH = 256;

  private final AdminConfigDao adminConfigDao;
  private final ObjectMapper objectMapper;

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
   * Validates a list of asset type strings. Rejects blank entries, duplicates,
   * types exceeding the max length, commas (business rule for readability),
   * and control characters.
   *
   * @param types the list to validate; null is treated as empty (no error).
   * @throws ClientException if any entry is invalid.
   */
  public void validateTypeInput(List<String> types) {
    if (types == null || types.isEmpty()) {
      return;
    }
    Set<String> seen = new HashSet<>();
    for (String type : types) {
      if (type == null || type.isBlank()) {
        throw new ClientException("Asset type must not be blank.");
      }
      if (type.length() > MAX_TYPE_LENGTH) {
        throw new ClientException(
            "Asset type exceeds maximum length of " + MAX_TYPE_LENGTH + " characters: "
            + type.substring(0, 32) + "...");
      }
      if (type.contains(",")) {
        throw new ClientException("Asset type must not contain commas: " + type);
      }
      for (char c : type.toCharArray()) {
        if (c < 32) {
          throw new ClientException("Asset type must not contain control characters: " + type);
        }
      }
      if (!seen.add(type)) {
        throw new ClientException("Duplicate asset type in list: " + type);
      }
    }
  }

  /**
   * Save the restriction configuration.
   */
  public void setConfig(boolean enabled, List<String> allowedTypes) {
    List<String> trimmedAllowedTypes = allowedTypes == null ? null
        : allowedTypes.stream().map(t -> t != null ? t.trim() : null).toList();
    validateTypeInput(trimmedAllowedTypes);
    adminConfigDao.setValue(KEY_ENABLED, String.valueOf(enabled));
    try {
      adminConfigDao.setValue(KEY_ALLOWED, objectMapper.writeValueAsString(trimmedAllowedTypes != null ? trimmedAllowedTypes : List.of()));
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
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse allowed types JSON: {}", json, e);
      return Collections.emptyList();
    }
  }
}
