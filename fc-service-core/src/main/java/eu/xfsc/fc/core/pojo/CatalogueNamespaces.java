package eu.xfsc.fc.core.pojo;

/**
 * Constants for the catalogue's protected RDF namespace.
 *
 * <p>The namespace {@value #FC_META_NAMESPACE} (prefix: {@value #FC_META_PREFIX})
 * is reserved for internal catalogue metadata. Only internal CAT processes may
 * write triples using terms in this namespace. External inputs (uploads, schema
 * imports) are filtered to remove any triples referencing this namespace.</p>
 *
 * @see <a href="https://projects.eclipse.org/projects/technology.xfsc">Eclipse XFSC</a>
 */
public final class CatalogueNamespaces {

  /**
   * Protected namespace URI for internal catalogue metadata.
   */
  public static final String FC_META_NAMESPACE =
      "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#";

  /**
   * Short prefix for the protected namespace.
   */
  public static final String FC_META_PREFIX = "fcmeta";

  // Reserved predicates for internal use (future extension)
  public static final String COMPLIANCE_CHECK_DATE = FC_META_NAMESPACE + "complianceCheckDate";
  public static final String COMPLIANCE_RESULT = FC_META_NAMESPACE + "complianceResult";
  public static final String VALIDATED_AGAINST = FC_META_NAMESPACE + "validatedAgainst";
  public static final String VALIDATION_TIMESTAMP = FC_META_NAMESPACE + "validationTimestamp";

  private CatalogueNamespaces() {
  }
}
