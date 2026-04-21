package eu.xfsc.fc.core.service.verification.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.verification.VerificationConstants;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.springframework.stereotype.Component;

/**
 * Extracts all RDF triples from non-credential RDF content using Apache Jena.
 *
 * <p>Supports JSON-LD, Turtle, N-Triples, and RDF/XML. Used exclusively in the
 * non-credential routing path ({@link eu.xfsc.fc.core.service.verification.CredentialFormat#UNKNOWN})
 * in {@code CredentialVerificationStrategy}. Must NOT be added to the static {@code extractors[]}
 * array — it is Spring-injected and invoked only from the early-return non-credential branch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JenaAllTriplesExtractor implements ClaimExtractor {

    private static final Lang[] FALLBACK_SEQUENCE = {Lang.JSONLD, Lang.TURTLE, Lang.NTRIPLES, Lang.RDFXML};

    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RdfClaim> extractClaims(ContentAccessor content) throws Exception {
        String body = content.getContentAsString();
        Lang lang = detectLang(content.getContentType());
        Model model = parseWithFallback(body, lang);
        List<RdfClaim> claims = new ArrayList<>();
        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            claims.add(toRdfClaim(it.nextStatement()));
        }
        log.debug("extractClaims; extracted {} triples", claims.size());
        return claims;
    }

    private Model parseWithFallback(String body, Lang primaryLang) {
        List<Lang> toTry = new ArrayList<>(FALLBACK_SEQUENCE.length);
        toTry.add(primaryLang);
        for (Lang lang : FALLBACK_SEQUENCE) {
            if (lang != primaryLang) {
                toTry.add(lang);
            }
        }
        RiotException lastException = null;
        for (Lang lang : toTry) {
            try {
                Model model = ModelFactory.createDefaultModel();
                RDFParser.fromString(body, lang).parse(model);
                return model;
            } catch (RiotException ex) {
                log.debug("parseWithFallback; lang {} failed: {}", lang, ex.getMessage());
                lastException = ex;
            }
        }
        throw lastException;
    }

    /**
     * Maps a MIME content type to a Jena {@link Lang}.
     * Falls back to {@link Lang#JSONLD} for absent or unrecognized content types.
     */
    static Lang detectLang(String contentType) {
        if (contentType == null) {
            return Lang.JSONLD;
        }
        return switch (contentType.strip().toLowerCase()) {
            case VerificationConstants.MEDIA_TYPE_TURTLE, "application/x-turtle" -> Lang.TURTLE;
            case VerificationConstants.MEDIA_TYPE_NTRIPLES, "text/plain" -> Lang.NTRIPLES;
            case VerificationConstants.MEDIA_TYPE_RDF_XML, "application/xml" -> Lang.RDFXML;
            default -> Lang.JSONLD;
        };
    }

    private RdfClaim toRdfClaim(Statement stmt) {
        return new RdfClaim(
                nodeToString(stmt.getSubject()),
                nodeToString(stmt.getPredicate()),
                nodeToString(stmt.getObject())
        );
    }

    /**
     * Formats an RDF node as a string, producing output identical to
     * {@link eu.xfsc.fc.core.pojo.RdfClaim}'s internal {@code rdf2String} method:
     * blank nodes as raw label, literals as JSON-serialized lexical form, IRIs as {@code <uri>}.
     */
    String nodeToString(RDFNode node) {
        if (node.isAnon()) {
            return node.asResource().getId().getLabelString();
        }
        if (node.isLiteral()) {
            try {
                return objectMapper.writeValueAsString(node.asLiteral().getLexicalForm());
            } catch (JsonProcessingException e) {
                log.error("nodeToString; failed to serialize literal: {}", node, e);
                return "\"" + node.asLiteral().getLexicalForm() + "\"";
            }
        }
        return "<" + node.asResource().getURI() + ">";
    }
}
