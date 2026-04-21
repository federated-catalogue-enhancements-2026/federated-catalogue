package eu.xfsc.fc.core.pojo;

import java.util.Objects;

import lombok.Getter;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.extern.slf4j.Slf4j;

/**
 * POJO Class for holding a Claim. A Claim is a triple represented by a subject, predicate, and object.
 */
@Slf4j
public class RdfClaim {

  @Getter
  private Statement triple;
  private final String subject;
  private final String predicate;
  private final String object;

  public RdfClaim(Statement triple, ObjectMapper objectMapper) {
    this.triple = triple;
    this.subject = rdf2String(triple.getSubject(), objectMapper);
    this.predicate = rdf2String(triple.getPredicate(), objectMapper);
    this.object = rdf2String(triple.getObject(), objectMapper);
  }

  public RdfClaim(String subject, String predicate, String object) {
    this.subject = subject;
    this.predicate = predicate;
    this.object = object;
  }

    public RDFNode getSubject() {
    return triple == null ? null : triple.getSubject();
  }

  public String getSubjectString() {
    return subject;
  }

  public String getSubjectValue() {
      return triple == null ? extractValue(subject) : nodeValue(triple.getSubject());
  }

  public RDFNode getPredicate() {
    return triple == null ? null : triple.getPredicate();
  }

  public String getPredicateString() {
    return predicate;
  }

  public String getPredicateValue() {
      return triple == null ? extractValue(predicate) : nodeValue(triple.getPredicate());
  }

  public RDFNode getObject() {
    return triple == null ? null : triple.getObject();
  }

  public String getObjectString() {
    return object;
  }

  public String getObjectValue() {
      return triple == null ? extractValue(object) : nodeValue(triple.getObject());
  }

  public String asTriple() {
    return String.format("%s %s %s . \n", getSubjectString(), getPredicateString(), getObjectString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(object, predicate, subject);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    RdfClaim other = (RdfClaim) obj;
    return Objects.equals(object, other.object) && Objects.equals(predicate, other.predicate)
        && Objects.equals(subject, other.subject);
  }

  @Override
  public String toString() {
    return "Claim[" + subject + " " + predicate + " " + object + "]";
  }

    /**
     * Serializes an RDF node to a string in the internal claim format:
     * blank nodes as their raw label, literals as a JSON-quoted string of the lexical form,
     * and IRIs wrapped in angle brackets ({@code <uri>}).
     *
     * <p>If JSON serialization of a literal fails, falls back to wrapping the lexical form
     * in double quotes directly, avoiding a {@link ClassCastException} from calling
     * {@code asResource()} on a literal node.</p>
     */
  private String rdf2String(RDFNode node, ObjectMapper objectMapper) {
    if (node.isAnon()) {
      return node.asResource().getId().getLabelString();
    } else if (node.isLiteral()) {
      try {
        return objectMapper.writeValueAsString(node.asLiteral().getLexicalForm());
      } catch (JsonProcessingException e) {
          log.error("rdf2String error for node {}", node, e);
          return "\"" + node.asLiteral().getLexicalForm() + "\"";
      }
    } else {
        return "<" + node.asResource().getURI() + ">";
    }
  }

    private static String extractValue(String encoded) {
        if (isIri(encoded)) {
            return encoded.substring(1, encoded.length() - 1);
        }
        if (isLiteral(encoded)) {
            return encoded.substring(1, encoded.length() - 1);
        }
        // is blank node: raw label string, return as is
        return encoded;
    }

    private static boolean isLiteral(String encoded) {
        return encoded.startsWith("\"") && encoded.endsWith("\"");
    }

    private static boolean isIri(String encoded) {
        return encoded.startsWith("<") && encoded.endsWith(">");
    }

    private String nodeValue(RDFNode node) {
    if (node.isAnon()) {
      return node.asResource().getId().getLabelString();
    }
    if (node.isLiteral()) {
      return node.asLiteral().getLexicalForm();
    }
    return node.asResource().getURI();
  }

}
