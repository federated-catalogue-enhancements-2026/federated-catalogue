package eu.xfsc.fc.core.service.verification.claims;

import java.util.ArrayList;
import java.util.List;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts RDF claims from the {@code credentialSubject} of a W3C Verifiable Credential
 * JSON-LD document. Uses Titanium JSON-LD for expansion and RDF conversion.
 */
@Slf4j
public class CredentialSubjectClaimExtractor implements ClaimExtractor {

  private static final String CREDENTIAL_SUBJECT_URI =
      "https://www.w3.org/2018/credentials#credentialSubject";
  private static final String VERIFIABLE_CREDENTIAL_URI =
      "https://www.w3.org/2018/credentials#verifiableCredential";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public List<CredentialClaim> extractClaims(ContentAccessor content) throws Exception {
    log.debug("extractClaims.enter; got content: {}", content);
    List<CredentialClaim> claims = new ArrayList<>();
    Document document = JsonDocument.of(content.getContentAsStream());
    JsonArray arr = JsonLd.expand(document).get();
    log.trace("extractClaims; expanded: {}", arr);
    JsonObject ld = arr.get(0).asJsonObject();
    if (ld.containsKey(VERIFIABLE_CREDENTIAL_URI)) {
      List<JsonValue> vcs = ld.get(VERIFIABLE_CREDENTIAL_URI).asJsonArray();
      for (JsonValue vcv : vcs) {
        JsonObject vc = vcv.asJsonObject();
        JsonArray graph = vc.get("@graph").asJsonArray();
        for (JsonValue val : graph) {
          addClaims(claims, val.asJsonObject());
        }
      }
    } else {
      // content contains just single VC
      addClaims(claims, ld);
    }
    log.debug("extractClaims.exit; returning claims: {}", claims.size());
    return claims;
  }

  private void addClaims(List<CredentialClaim> claims, JsonObject vc) throws JsonLdError {
    JsonArray css = vc.getJsonArray(CREDENTIAL_SUBJECT_URI);
    for (JsonValue cs : css) {
      Document csDoc = JsonDocument.of(cs.asJsonObject());
      // var avoids explicit deprecated RdfDataset/RdfGraph/RdfTriple/RdfValue imports;
      // method calls on inferred types require no import
      var rdf = JsonLd.toRdf(csDoc).produceGeneralizedRdf(true).get();
      Model model = ModelFactory.createDefaultModel();
      for (var triple : rdf.getDefaultGraph().toList()) {
        log.trace("extractClaims; got triple: {}", triple);
        var subject = triple.getSubject();
        Resource subjectRes = subject.isBlankNode()
            ? model.createResource(new AnonId(subject.getValue()))
            : model.createResource(subject.getValue());
        Property property = model.createProperty(triple.getPredicate().getValue());
        var object = triple.getObject();
        RDFNode objectNode;
        if (object.isBlankNode()) {
          objectNode = model.createResource(new AnonId(object.getValue()));
        } else if (object.isLiteral()) {
          var literal = object.asLiteral();
          String dtype = literal.getDatatype();
          if (dtype != null) {
            objectNode = model.createTypedLiteral(literal.getValue(),
                TypeMapper.getInstance().getSafeTypeByName(dtype));
          } else {
            String lang = literal.getLanguage().orElse(null);
            objectNode = lang != null
                ? model.createLiteral(literal.getValue(), lang)
                : model.createLiteral(literal.getValue());
          }
        } else {
          objectNode = model.createResource(object.getValue());
        }
        Statement stmt = model.createStatement(subjectRes, property, objectNode);
        model.add(stmt);
        claims.add(new CredentialClaim(stmt, objectMapper));
      }
    }
  }

}
