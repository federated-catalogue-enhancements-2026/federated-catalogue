package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-role configuration entry from a bundle's {@code framework.yaml}.
 *
 * <p>{@code additionalRoots} enables SHACL sibling-class grouping: explicit URIs that
 * resolve to the role even though they are not OWL subclasses of its primary root.
 * The mechanism is general; reach for it only when an external ontology genuinely
 * declares siblings without subsumption. The current gx-2511 use (DigitalServiceOffering
 * routed to ServiceOffering) is known semantic debt: the catalogue would otherwise have
 * no landing site for DSO credentials because the role -> result-POJO dispatch is still
 * hardcoded to Gaia-X roles. Routing DSO through ServiceOffering makes the catalogue
 * silently assert an ontology-level equivalence the spec does not declare.
 * {@code types} is defined for forward-compatibility with JSON Schema validation engines
 * and is intentionally unused in the SHACL resolver until other trust frameworks with different validations are integrated.
 * {@code resultType} is a stable string identifier that maps this role to a result-materialization strategy;
 * an empty value signals that the generic result schema is used.
 */
public record RoleConfig(
    @JsonProperty("additional_roots") List<String> additionalRoots,
    @JsonProperty("types") List<String> types,
    @JsonProperty("result_type") String resultType
) {

  public RoleConfig {
    additionalRoots = additionalRoots != null ? additionalRoots : List.of();
    types = types != null ? types : List.of();
    resultType = resultType != null ? resultType.strip() : "";
  }
}
