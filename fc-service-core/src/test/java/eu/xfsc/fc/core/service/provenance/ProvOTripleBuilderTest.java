package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.pojo.RdfClaim;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProvOTripleBuilderTest {

  private static final String PROV_NS = "http://www.w3.org/ns/prov#";
  private static final String ASSET_ID = "did:web:example:asset:v1";
  private static final String OBJECT_VALUE = "did:web:example:activity";

  static Stream<Arguments> provenanceTypeMappings() {
    return Stream.of(
        Arguments.of(ProvenanceType.CREATION, PROV_NS + "wasGeneratedBy"),
        Arguments.of(ProvenanceType.DERIVATION, PROV_NS + "wasDerivedFrom"),
        Arguments.of(ProvenanceType.ATTRIBUTION, PROV_NS + "wasAttributedTo"),
        Arguments.of(ProvenanceType.MODIFICATION, PROV_NS + "wasRevisionOf")
    );
  }

  @ParameterizedTest
  @MethodSource("provenanceTypeMappings")
  void build_provenanceType_mapsToCorrectPredicate(ProvenanceType type, String expectedPredicate) {
    List<RdfClaim> triples = ProvOTripleBuilder.build(ASSET_ID, type, OBJECT_VALUE);

    assertEquals(1, triples.size());
    RdfClaim triple = triples.getFirst();
    assertEquals("<" + ASSET_ID + ">", triple.getSubjectString());
    assertEquals("<" + expectedPredicate + ">", triple.getPredicateString());
    assertEquals("<" + OBJECT_VALUE + ">", triple.getObjectString());
  }
}
