package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import org.junit.jupiter.api.Test;

class XmlSchemaValidatorTest {

  private static final String VALID_XSD =
      "<?xml version=\"1.0\"?>"
      + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
      + "  <xs:element name=\"root\">"
      + "    <xs:complexType>"
      + "      <xs:sequence>"
      + "        <xs:element name=\"name\" type=\"xs:string\"/>"
      + "      </xs:sequence>"
      + "    </xs:complexType>"
      + "  </xs:element>"
      + "</xs:schema>";

  private static final String CONFORMING_XML = "<root><name>test</name></root>";

  private static final String NON_CONFORMING_XML = "<root><unknown>x</unknown></root>";

  private final XmlSchemaValidator validator = new XmlSchemaValidator();

  @Test
  void validate_conformingXml_returnsConforming() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_XML);
    ContentAccessorDirect schema = new ContentAccessorDirect(VALID_XSD);

    ValidationReport report = validator.validate(asset, schema);

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_nonConformingXml_returnsViolation() {
    ContentAccessorDirect asset = new ContentAccessorDirect(NON_CONFORMING_XML);
    ContentAccessorDirect schema = new ContentAccessorDirect(VALID_XSD);

    ValidationReport report = validator.validate(asset, schema);

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
  }

  @Test
  void validate_malformedSchema_throwsClientException() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_XML);
    ContentAccessorDirect schema = new ContentAccessorDirect("not valid xml schema <<<");

    assertThrows(ClientException.class, () -> validator.validate(asset, schema));
  }

  @Test
  void validate_malformedAsset_returnsViolation() {
    ContentAccessorDirect asset = new ContentAccessorDirect("not valid xml <<<");
    ContentAccessorDirect schema = new ContentAccessorDirect(VALID_XSD);

    ValidationReport report = validator.validate(asset, schema);

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
  }

  @Test
  void validate_schemaWithDoctype_throwsClientException() {
    ContentAccessorDirect asset = new ContentAccessorDirect(CONFORMING_XML);
    ContentAccessorDirect schema = new ContentAccessorDirect(
        "<?xml version=\"1.0\"?><!DOCTYPE xs:schema SYSTEM \"http://evil.com/evil.dtd\">"
        + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<xs:element name=\"root\" type=\"xs:string\"/></xs:schema>");

    assertThrows(ClientException.class, () -> validator.validate(asset, schema));
  }
}
