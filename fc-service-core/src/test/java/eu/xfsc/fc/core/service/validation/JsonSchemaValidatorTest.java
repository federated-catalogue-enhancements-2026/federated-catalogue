package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  private static final String SIMPLE_SCHEMA =
      "{\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}";

  private static final String CONFORMING_JSON = "{\"id\":\"abc-123\"}";

  private static final String NON_CONFORMING_JSON = "{\"value\":\"missing-id-field\"}";

  private final JsonSchemaValidator validator = new JsonSchemaValidator(new ObjectMapper());

  @Test
  void validate_conformingJson_returnsConforming() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_JSON);
    ContentAccessorDirect schema = new ContentAccessorDirect(SIMPLE_SCHEMA);

    ValidationReport report = validator.validate(asset, schema);

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_nonConformingJson_returnsViolation() {
    ContentAccessorDirect asset = new ContentAccessorDirect(NON_CONFORMING_JSON);
    ContentAccessorDirect schema = new ContentAccessorDirect(SIMPLE_SCHEMA);

    ValidationReport report = validator.validate(asset, schema);

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
    assertNotNull(report.getViolations().get(0).getMessage());
  }

  @Test
  void validate_malformedJson_throwsClientException() {
    ContentAccessorDirect asset = new ContentAccessorDirect("not json");
    ContentAccessorDirect schema = new ContentAccessorDirect(SIMPLE_SCHEMA);

    assertThrows(ClientException.class, () -> validator.validate(asset, schema));
  }

  @Test
  void validate_fileRefInSchema_throwsClientException() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_JSON);
    ContentAccessorDirect schema = new ContentAccessorDirect(
        "{\"$ref\":\"file:///etc/passwd\"}");

    assertThrows(ClientException.class, () -> validator.validate(asset, schema));
  }

  @Test
  void validate_httpRefInSchema_throwsClientException() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_JSON);
    ContentAccessorDirect schema = new ContentAccessorDirect(
        "{\"$ref\":\"http://169.254.169.254/latest/meta-data\"}");

    assertThrows(ClientException.class, () -> validator.validate(asset, schema));
  }

  @Test
  void validate_gopherRefInSchema_throwsClientException() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_JSON);
    ContentAccessorDirect schema = new ContentAccessorDirect(
        "{\"$ref\":\"gopher://internal/resource\"}");

    assertThrows(ClientException.class, () -> validator.validate(asset, schema));
  }

  @Test
  void validate_relativeRefInSchema_doesNotThrow() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_JSON);
    ContentAccessorDirect schema = new ContentAccessorDirect(
        "{\"type\":\"object\",\"required\":[\"id\"],"
        + "\"properties\":{\"id\":{\"$ref\":\"#/definitions/IdType\"}},"
        + "\"definitions\":{\"IdType\":{\"type\":\"string\"}}}");

    assertDoesNotThrow(() -> validator.validate(asset, schema));
  }
}
