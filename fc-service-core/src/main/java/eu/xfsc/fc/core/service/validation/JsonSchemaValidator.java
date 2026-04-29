package eu.xfsc.fc.core.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Validates non-RDF JSON assets against JSON Schema (Draft 2020-12).
 *
 * <p>Uses the networknt json-schema-validator 2.x API with {@link SchemaRegistry}.</p>
 */
@Slf4j
@Service
public class JsonSchemaValidator {

  private final ObjectMapper objectMapper;

  public JsonSchemaValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Validates the given JSON asset content against the provided JSON Schema.
   *
   * @param assetContent  ContentAccessor with the JSON asset to validate
   * @param schemaContent ContentAccessor with the JSON Schema to validate against
   * @return validation report with conforms flag, violations, and error messages
   */
  public ValidationReport validate(ContentAccessor assetContent, ContentAccessor schemaContent) {
    try {
      JsonNode schemaNode = objectMapper.readTree(schemaContent.getContentAsString());
      validateNoExternalRefs(schemaNode);

      JsonNode contentNode = objectMapper.readTree(assetContent.getContentAsStream());

      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
      Schema schema = registry.getSchema(schemaNode);

      List<Error> errors = schema.validate(contentNode);
      return toValidationReport(errors);
    } catch (IOException e) {
      throw new ClientException("Invalid JSON schema or asset content: " + e.getMessage(), e);
    }
  }

  /**
   * Rejects schemas containing external {@code $ref} URIs — prevents SSRF via uploaded schemas.
   * Only data: URIs and relative references (no scheme) are permitted.
   */
  private void validateNoExternalRefs(JsonNode node) {
    if (node.isObject()) {
      JsonNode ref = node.get("$ref");
      if (ref != null && ref.isTextual()) {
        String value = ref.asText();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("ftp://")) {
          throw new ClientException("Schema contains external $ref '" + value + "' which is not permitted");
        }
      }
      node.fields().forEachRemaining(entry -> validateNoExternalRefs(entry.getValue()));
    } else if (node.isArray()) {
      node.elements().forEachRemaining(this::validateNoExternalRefs);
    }
  }

  private ValidationReport toValidationReport(List<Error> errors) {
    boolean conforms = errors.isEmpty();
    ValidationReport report = new ValidationReport();
    report.setConforms(conforms);

    if (conforms) {
      report.setViolations(List.of());
      return report;
    }

    List<ValidationViolation> violations = errors.stream()
        .map(this::toViolation)
        .toList();
    report.setViolations(violations);
    report.setRawReport(errors.stream()
        .map(Error::getMessage)
        .reduce((a, b) -> a + "\n" + b)
        .orElse(null));
    return report;
  }

  private ValidationViolation toViolation(Error error) {
    ValidationViolation violation = new ValidationViolation();
    violation.setFocusNode(error.getInstanceLocation() != null
        ? error.getInstanceLocation().toString() : null);
    violation.setMessage(error.getMessage());
    violation.setSeverity(ValidationViolation.SeverityEnum.VIOLATION);
    violation.setSourceShape(error.getSchemaLocation() != null
        ? error.getSchemaLocation().toString() : null);
    return violation;
  }
}
