package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import eu.xfsc.fc.core.exception.ClientException;
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
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
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
    try {
      Schema xsdSchema = loadXsdSchema(schemaContent);
      // A new Validator instance per call: Validator is not thread-safe
      Validator validator = xsdSchema.newValidator();
      // XXE prevention on the content validator
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

      try (InputStream contentStream = assetContent.getContentAsStream()) {
        validator.validate(new StreamSource(contentStream));
        return new ValidationReport().conforms(true).violations(List.of());
      } catch (SAXException e) {
        return new ValidationReport()
            .conforms(false)
            .violations(List.of(new ValidationViolation()
                .message(e.getMessage())
                .severity(ValidationViolation.SeverityEnum.VIOLATION)))
            .rawReport(e.getMessage());
      }
    } catch (ParserConfigurationException e) {
      throw new ServerException("XML parser configuration error: " + e.getMessage(), e);
    } catch (SAXException | IOException e) {
      throw new ClientException("Invalid XML schema or asset content: " + e.getMessage(), e);
    }
  }

  private Schema loadXsdSchema(ContentAccessor schemaContent)
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
    // XXE prevention — FEATURE_SECURE_PROCESSING disables inline schemas and limits entity expansion;
    // ACCESS_EXTERNAL_* blocks all URI-based DTD and schema loading.
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newSchema(new DOMSource(schemaDoc));
  }
}
