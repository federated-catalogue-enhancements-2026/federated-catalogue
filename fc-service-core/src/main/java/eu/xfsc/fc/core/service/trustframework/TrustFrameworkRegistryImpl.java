package eu.xfsc.fc.core.service.trustframework;

import eu.xfsc.fc.core.pojo.ContentAccessor;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrustFrameworkRegistryImpl implements TrustFrameworkRegistry {

  private final Map<String, ResolvedRole> typeIndex;
  private final Map<String, TrustFrameworkBundle> bundleIndex;
  private final Set<String> activeProfileIds;

  public TrustFrameworkRegistryImpl(List<TrustFrameworkBundle> bundles) {
    this.bundleIndex = new HashMap<>();
    this.typeIndex = new HashMap<>();
    var active = new java.util.HashSet<String>();
    for (TrustFrameworkBundle bundle : bundles) {
      String profileId = bundle.config().id();
      if (bundleIndex.containsKey(profileId)) {
        log.warn("Duplicate bundle ID '{}' ignored — first registration wins", profileId);
        continue;
      }
      bundleIndex.put(profileId, bundle);
      if (bundle.config().validationType() == ValidationType.SHACL) {
        validateNamespace(bundle.config());
        indexBundle(bundle);
        active.add(profileId);
      } else {
        log.warn("Bundle '{}' has validationType '{}' — validation engine not yet wired, bundle deferred",
            profileId, bundle.config().validationType());
      }
    }
    this.activeProfileIds = Set.copyOf(active);
  }

  private static void validateNamespace(FrameworkBundleConfig config) {
    if (config.roles().isEmpty()) {
      return;
    }
    String ns = config.namespace();
    if (ns == null) {
      throw new IllegalArgumentException(
          "Bundle '" + config.id() + "' has roles but namespace is null");
    }
    if (!ns.endsWith("/") && !ns.endsWith("#")) {
      throw new IllegalArgumentException(
          "Bundle '" + config.id() + "' namespace '" + ns + "' must end with '/' or '#'");
    }
  }

  private void indexBundle(TrustFrameworkBundle bundle) {
    if (bundle.ontology() == null) {
      log.warn("Bundle '{}' has validationType SHACL but no ontology — subclass walk skipped", bundle.config().id());
    }
    OntModel model = loadOntModel(bundle.ontology());
    var config = bundle.config();
    config.roles().forEach((roleName, roleConfig) -> {
      var resolved = new ResolvedRole(config.id(), roleName);
      indexTypeAndSubclasses(config.namespace() + roleName, resolved, model);
      for (String additionalRoot : roleConfig.additionalRoots()) {
        if (additionalRoot == null) {
          log.warn("Bundle '{}' role '{}' has a null additionalRoot entry — skipped", config.id(), roleName);
          continue;
        }
        indexTypeAndSubclasses(additionalRoot, resolved, model);
      }
    });
  }

  private OntModel loadOntModel(ContentAccessor ontology) {
    OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
    if (ontology != null) {
      model.read(new StringReader(ontology.getContentAsString()), null, Lang.TURTLE.getName());
    }
    return model;
  }

  private void indexTypeAndSubclasses(String rootUri, ResolvedRole resolved, OntModel model) {
    if (!isValidSparqlUri(rootUri)) {
      log.warn("Skipping URI '{}' — contains characters unsafe for SPARQL injection", rootUri);
      return;
    }
    typeIndex.putIfAbsent(rootUri, resolved);
    String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
        + "SELECT ?sub WHERE { ?sub rdfs:subClassOf <" + rootUri + "> FILTER(?sub != <" + rootUri + ">) }";
    try (var qe = QueryExecutionFactory.create(QueryFactory.create(query), model)) {
      qe.execSelect().forEachRemaining(row -> {
        Resource res = row.getResource("sub");
        if (res != null && res.isURIResource()) {
          typeIndex.putIfAbsent(res.getURI(), resolved);
        }
      });
    }
  }

  /**
   * Returns false if the URI contains characters that would break a SPARQL string literal.
   */
  private static boolean isValidSparqlUri(String uri) {
    return uri != null
        && !uri.contains(">")
        && !uri.contains("<")
        && !uri.contains(" ")
        && !uri.contains("\n")
        && !uri.contains("\r");
  }

  @Override
  public ResolvedRole resolveRole(String typeUri) {
    return typeIndex.getOrDefault(typeUri, ResolvedRole.UNKNOWN);
  }

  @Override
  public Collection<TrustFrameworkBundle> getBundles() {
    return List.copyOf(bundleIndex.values());
  }

  @Override
  public Optional<TrustFrameworkBundle> getBundle(String profileId) {
    return Optional.ofNullable(bundleIndex.get(profileId));
  }

  @Override
  public Set<String> getEffectiveRoles(String profileId) {
    return bundleIndex.containsKey(profileId)
        ? Set.copyOf(bundleIndex.get(profileId).config().roles().keySet())
        : Set.of();
  }

  @Override
  public boolean isFrameworkEnabled(String profileId) {
    return activeProfileIds.contains(profileId);
  }
}
