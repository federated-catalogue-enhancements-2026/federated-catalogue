package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Validates non-RDF XML assets against an XML Schema (XSD 1.0).
 *
 * <p>Uses {@code javax.xml.validation} (default XSD 1.0, same as schema upload analysis).
 * The {@link SchemaFactory} and {@link javax.xml.validation.Validator} are hardened against
 * XXE attacks by disabling external DTD and schema access.</p>
 */
@Slf4j
@Service
@NoArgsConstructor
public class XmlSchemaValidator {

  /**
   * Validates the given XML asset content against the provided XSD schema.
   *
   * <p>A new {@link javax.xml.validation.Validator} is created per call as the class
   * is not thread-safe.</p>
   *
   * @param assetContent  ContentAccessor with the XML asset to validate
   * @param schemaContent ContentAccessor with the XSD schema to validate against
   * @return validation report with conforms flag and SAX error on violation
   */
  public ValidationReport validate(ContentAccessor assetContent, ContentAccessor schemaContent) {
    log.debug("validate.enter; XML Schema validation");
    try {
      javax.xml.validation.Schema xsdSchema = loadXsdSchema(schemaContent);
      // A new Validator instance per call: Validator is not thread-safe
      javax.xml.validation.Validator validator = xsdSchema.newValidator();
      // XXE prevention on the content validator
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

      try (InputStream contentStream = assetContent.getContentAsStream()) {
        validator.validate(new StreamSource(contentStream));
        ValidationReport report = new ValidationReport();
        report.setConforms(true);
        report.setViolations(List.of());
        return report;
      } catch (SAXException e) {
        ValidationViolation violation = new ValidationViolation();
        violation.setMessage(e.getMessage());
        violation.setSeverity(ValidationViolation.SeverityEnum.VIOLATION);
        ValidationReport report = new ValidationReport();
        report.setConforms(false);
        report.setViolations(List.of(violation));
        report.setRawReport(e.getMessage());
        return report;
      }
    } catch (IOException | ParserConfigurationException | SAXException e) {
      throw new ServerException("XML Schema validation failed: " + e.getMessage(), e);
    }
  }

  private javax.xml.validation.Schema loadXsdSchema(ContentAccessor schemaContent)
      throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    // XXE prevention — defense-in-depth per OWASP XXE cheat sheet:
    // disallow-doctype-decl blocks DOCTYPE entirely (primary mitigation);
    // the entity-entity features cover parsers that ignore disallow-doctype-decl.
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    dbf.setExpandEntityReferences(false);
    Document schemaDoc;
    try (InputStream schemaStream = schemaContent.getContentAsStream()) {
      schemaDoc = dbf.newDocumentBuilder().parse(schemaStream);
    }

    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    // XXE prevention on the schema factory itself
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newSchema(new DOMSource(schemaDoc));
  }
}
