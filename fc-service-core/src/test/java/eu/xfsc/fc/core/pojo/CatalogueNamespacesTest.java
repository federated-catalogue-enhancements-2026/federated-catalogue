package eu.xfsc.fc.core.pojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link CatalogueNamespaces} constants for the protected namespace.
 */
class CatalogueNamespacesTest {

  @Test
  void namespaceMatchesSpec() {
    assertEquals(
        "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#",
        CatalogueNamespaces.FC_META_NAMESPACE);
  }

  @Test
  void prefixMatchesSpec() {
    assertEquals("fcmeta", CatalogueNamespaces.FC_META_PREFIX);
  }

  @Test
  void reservedPredicatesUseNamespace() {
    assertTrue(CatalogueNamespaces.COMPLIANCE_CHECK_DATE.startsWith(CatalogueNamespaces.FC_META_NAMESPACE));
    assertTrue(CatalogueNamespaces.COMPLIANCE_RESULT.startsWith(CatalogueNamespaces.FC_META_NAMESPACE));
    assertTrue(CatalogueNamespaces.VALIDATED_AGAINST.startsWith(CatalogueNamespaces.FC_META_NAMESPACE));
    assertTrue(CatalogueNamespaces.VALIDATION_TIMESTAMP.startsWith(CatalogueNamespaces.FC_META_NAMESPACE));
  }

  @Test
  void reservedPredicateLocalNames() {
    assertEquals(CatalogueNamespaces.FC_META_NAMESPACE + "complianceCheckDate",
        CatalogueNamespaces.COMPLIANCE_CHECK_DATE);
    assertEquals(CatalogueNamespaces.FC_META_NAMESPACE + "complianceResult",
        CatalogueNamespaces.COMPLIANCE_RESULT);
    assertEquals(CatalogueNamespaces.FC_META_NAMESPACE + "validatedAgainst",
        CatalogueNamespaces.VALIDATED_AGAINST);
    assertEquals(CatalogueNamespaces.FC_META_NAMESPACE + "validationTimestamp",
        CatalogueNamespaces.VALIDATION_TIMESTAMP);
  }
}
