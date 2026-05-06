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
import org.apache.jena.riot.Lang;

public class TrustFrameworkRegistryImpl implements TrustFrameworkRegistry {

  private final Map<String, ResolvedRole> typeIndex;
  private final Map<String, TrustFrameworkBundle> bundleIndex;

  public TrustFrameworkRegistryImpl(List<TrustFrameworkBundle> bundles) {
    this.bundleIndex = new HashMap<>();
    this.typeIndex = new HashMap<>();
    for (TrustFrameworkBundle bundle : bundles) {
      bundleIndex.put(bundle.config().id(), bundle);
      if (bundle.config().validationType() == ValidationType.SHACL) {
        indexBundle(bundle);
      }
    }
  }

  private void indexBundle(TrustFrameworkBundle bundle) {
    OntModel model = loadOntModel(bundle.ontology());
    var config = bundle.config();
    config.roles().forEach((roleName, roleConfig) -> {
      var resolved = new ResolvedRole(config.id(), roleName);
      indexTypeAndSubclasses(config.namespace() + roleName, resolved, model);
      for (String additionalRoot : roleConfig.additionalRoots()) {
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
    typeIndex.put(rootUri, resolved);
    String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        "SELECT ?sub WHERE { ?sub rdfs:subClassOf <" + rootUri + "> FILTER(?sub != <" + rootUri + ">) }";
    try (var qe = QueryExecutionFactory.create(QueryFactory.create(query), model)) {
      qe.execSelect().forEachRemaining(row ->
          typeIndex.putIfAbsent(row.getResource("sub").getURI(), resolved));
    }
  }

  @Override
  public ResolvedRole resolveRole(String typeUri) {
    return typeIndex.getOrDefault(typeUri, ResolvedRole.UNKNOWN);
  }

  @Override
  public Collection<TrustFrameworkBundle> getBundles() {
    return bundleIndex.values();
  }

  @Override
  public Optional<TrustFrameworkBundle> getBundle(String profileId) {
    return Optional.ofNullable(bundleIndex.get(profileId));
  }

  @Override
  public Set<String> getEffectiveRoles(String profileId) {
    return bundleIndex.containsKey(profileId)
        ? bundleIndex.get(profileId).config().roles().keySet()
        : Set.of();
  }

  @Override
  public boolean isFrameworkEnabled(String profileId) {
    return bundleIndex.containsKey(profileId);
  }
}
