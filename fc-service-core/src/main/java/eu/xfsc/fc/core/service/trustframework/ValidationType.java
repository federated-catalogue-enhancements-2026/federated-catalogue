package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ValidationType {
  SHACL,
  JSON_SCHEMA,
  UNKNOWN;

  @JsonCreator
  public static ValidationType fromString(String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.toUpperCase().replace("-", "_"));
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
