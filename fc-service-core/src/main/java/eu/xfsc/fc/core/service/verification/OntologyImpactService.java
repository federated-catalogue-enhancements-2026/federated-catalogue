package eu.xfsc.fc.core.service.verification;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.OntologyImpactEntry;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.RoleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes the per-ontology contribution of runtime-uploaded ontologies to each
 * registered trust-framework role.
 *
 * <p>For every {@link SchemaType#ONTOLOGY} row in the schema store, this service parses
 * the ontology with Jena and counts the {@code rdfs:subClassOf+} descendants reachable
 * from each registered role's primary root URI and its {@code additional_roots} siblings.
 * The result is surfaced to the admin schema-validation page so the OWL toggle's effect
 * is concrete: admins can see what each stored ontology contributes before flipping the
 * toggle off.
 *
 * <p>The computation is read-only and consults only data already in the catalogue. No
 * caching is in place — Jena parsing is fast for the expected ontology count; add
 * {@code @Cacheable} keyed on the schema content hash if measurements show it slow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OntologyImpactService {

  private static final String SUBCLASS_QUERY =
      "SELECT ?sub WHERE { ?sub rdfs:subClassOf+ ?root FILTER(?sub != ?root) }";

  private final SchemaStore schemaStore;
  private final TrustFrameworkRegistry trustFrameworkRegistry;

  /**
   * Returns one entry per stored ontology, with a map of role name to subclass count.
   * Roles with zero contributions are omitted from the per-entry map.
   *
   * @return list of impact entries; empty if no ontologies are stored
   */
  public List<OntologyImpactEntry> computeImpact() {
    Map<SchemaType, List<String>> schemaList = schemaStore.getSchemaList();
    List<String> ontologyIds = schemaList.getOrDefault(SchemaType.ONTOLOGY, List.of());
    if (ontologyIds.isEmpty()) {
      return List.of();
    }

    Map<String, Set<String>> roleRoots = collectRoleRoots();
    List<OntologyImpactEntry> entries = new ArrayList<>(ontologyIds.size());
    for (String schemaId : ontologyIds) {
      SchemaRecord record;
      try {
        record = schemaStore.getSchemaRecord(schemaId);
      } catch (RuntimeException ex) {
        log.warn("computeImpact; could not load schema record '{}': {}", schemaId, ex.getMessage());
        continue;
      }
      entries.add(buildEntry(record, roleRoots));
    }
    return entries;
  }

  /**
   * Collects all role root URIs (primary + additional_roots) across every active bundle,
   * keyed by role name. Counts under the same role name from different bundles are
   * aggregated.
   */
  private Map<String, Set<String>> collectRoleRoots() {
    Map<String, Set<String>> roleRoots = new HashMap<>();
    Collection<TrustFrameworkBundle> bundles = trustFrameworkRegistry.getActiveBundles();
    for (TrustFrameworkBundle bundle : bundles) {
      FrameworkBundleConfig config = bundle.config();
      String namespace = config.namespace();
      for (Map.Entry<String, RoleConfig> roleEntry : config.roles().entrySet()) {
        String roleName = roleEntry.getKey();
        Set<String> rootsForRole = roleRoots.computeIfAbsent(roleName, k -> new HashSet<>());
        rootsForRole.add(namespace + roleName);
        for (String additionalRoot : roleEntry.getValue().additionalRoots()) {
          if (additionalRoot != null && !additionalRoot.isBlank()) {
            rootsForRole.add(additionalRoot);
          }
        }
      }
    }
    return roleRoots;
  }

  private OntologyImpactEntry buildEntry(SchemaRecord record, Map<String, Set<String>> roleRoots) {
    OntologyImpactEntry entry = new OntologyImpactEntry()
        .id(record.getId())
        .name(record.getId())
        .uploadedAt(record.createdAt())
        .contributions(new HashMap<>());

    OntModel model = parseOntology(record);
    if (model == null) {
      return entry;
    }

    for (Map.Entry<String, Set<String>> roleEntry : roleRoots.entrySet()) {
      Set<String> subclasses = new HashSet<>();
      for (String root : roleEntry.getValue()) {
        subclasses.addAll(querySubclasses(model, root));
      }
      if (!subclasses.isEmpty()) {
        entry.putContributionsItem(roleEntry.getKey(), subclasses.size());
      }
    }
    return entry;
  }

  private OntModel parseOntology(SchemaRecord record) {
    // No inference: we only want explicit rdfs:subClassOf edges. The `+` operator in the
    // SPARQL query handles transitive closure. A reasoner like OWL_MEM_MICRO_RULE_INF
    // would otherwise count owl:Nothing as a subclass of every class.
    OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
    try {
      model.read(new StringReader(record.content()), null, Lang.TURTLE.getName());
    } catch (RuntimeException ex) {
      log.warn("computeImpact; could not parse ontology '{}' as Turtle: {}",
          record.getId(), ex.getMessage());
      return null;
    }
    return model;
  }

  private Set<String> querySubclasses(OntModel model, String rootUri) {
    Set<String> result = new HashSet<>();
    ParameterizedSparqlString pss = new ParameterizedSparqlString();
    pss.setNsPrefix("rdfs", RDFS.getURI());
    pss.setCommandText(SUBCLASS_QUERY);
    try {
      pss.setIri("root", rootUri);
    } catch (RuntimeException ex) {
      log.warn("computeImpact; could not bind root URI '{}': {}", rootUri, ex.getMessage());
      return result;
    }
    try (var qe = QueryExecutionFactory.create(pss.asQuery(), model)) {
      qe.execSelect().forEachRemaining(row -> {
        Resource res = row.getResource("sub");
        if (res != null && res.isURIResource()) {
          result.add(res.getURI());
        }
      });
    } catch (RuntimeException ex) {
      log.warn("computeImpact; SPARQL subclass query for root '{}' failed: {}",
          rootUri, ex.getMessage());
    }
    return result;
  }
}
