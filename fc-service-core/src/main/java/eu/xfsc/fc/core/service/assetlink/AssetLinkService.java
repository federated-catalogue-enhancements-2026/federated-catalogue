package eu.xfsc.fc.core.service.assetlink;

import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assetlinks.AssetLink;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkRepository;
import eu.xfsc.fc.core.dao.assetlinks.AssetLinkType;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for bidirectional asset link management.
 *
 * <p>Links are stored in PostgreSQL ({@code asset_links} table) as the primary store.
 * Corresponding RDF triples in the {@code fcmeta:} namespace are written to the graph DB
 * as supplementary data. Graph writes are non-fatal: a failure is logged but does not
 * roll back the PostgreSQL transaction.</p>
 *
 * <p>The {@code fcmeta:} namespace is protected at ingestion boundaries by
 * {@link eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter}. Link triples
 * are written internally via {@link GraphStore#addClaims}, bypassing that filter.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLinkService {

  private static final String PREDICATE_HAS_HUMAN_READABLE = "hasHumanReadable";
  private static final String PREDICATE_HAS_MACHINE_READABLE = "hasMachineReadable";

  private final AssetLinkRepository assetLinkRepository;
  private final GraphStore graphDb;
  private final ProtectedNamespaceProperties namespaceProperties;

  /**
   * Create a bidirectional link between two assets in PostgreSQL and write
   * corresponding RDF triples to the graph DB.
   *
   * <p>Two rows are inserted: {@code (sourceId → targetId, linkType)} and
   * {@code (targetId → sourceId, inverse(linkType))}. The unique constraint
   * {@code uq_asset_link} on {@code (source_id, target_id, link_type)} prevents
   * concurrent duplicate inserts from producing orphan rows.</p>
   *
   * @param sourceId  IRI of the source asset (machine-readable)
   * @param targetId  IRI of the target asset (human-readable)
   * @param linkType  direction of the forward link
   * @param createdBy DID of the user creating the link
   * @throws ConflictException if the source asset already has a link of the given type
   */
  @Transactional
  public void createLink(String sourceId, String targetId, AssetLinkType linkType, String createdBy) {
    if (assetLinkRepository.findBySourceIdAndLinkType(sourceId, linkType).isPresent()) {
      throw new ConflictException(
          String.format("Asset '%s' already has a %s link", sourceId, linkType));
    }

    final var forward = buildLink(sourceId, targetId, linkType, createdBy);
    final var reverse = buildLink(targetId, sourceId, invertLinkType(linkType), createdBy);
    assetLinkRepository.saveAll(List.of(forward, reverse));

    try {
      writeLinkTriples(sourceId, targetId);
    } catch (Exception ex) {
      log.warn("createLink; graph triple write failed for {}->{} (non-fatal): {}", sourceId, targetId, ex.getMessage());
    }
  }

  /**
   * Resolve the IRI of the asset linked from the given asset by link type.
   *
   * @param assetId  IRI of the asset to look up from
   * @param linkType the direction to resolve
   * @return the linked asset IRI, or empty if no link exists
   */
  @Transactional(readOnly = true)
  public Optional<String> getLinkedAsset(String assetId, AssetLinkType linkType) {
    return assetLinkRepository.findBySourceIdAndLinkType(assetId, linkType)
        .map(AssetLink::getTargetId);
  }

  /**
   * Remove all link rows where the given asset IRI appears as source or target,
   * and delete the corresponding graph triples.
   *
   * @param assetId IRI of the asset whose links should be removed
   */
  @Transactional
  public void deleteLinksForAsset(String assetId) {
    log.debug("deleteLinksForAsset; removing link rows for asset: {}", assetId);
    try {
      graphDb.deleteClaims(assetId);
    } catch (Exception ex) {
      log.warn("deleteLinksForAsset; graph triple deletion failed for {} (non-fatal): {}", assetId, ex.getMessage());
    }
    assetLinkRepository.deleteBySourceIdOrTargetId(assetId, assetId);
  }

  /**
   * Return all link rows for the given asset IRI where the asset is the source.
   * Returns a map from link type to target IRI for efficient single-query enrichment.
   *
   * @param assetId IRI of the asset to look up from
   * @return map of link type to linked asset IRI; empty if no links exist
   */
  @Transactional(readOnly = true)
  public Map<AssetLinkType, String> getLinkedAssets(String assetId) {
    return assetLinkRepository.findBySourceId(assetId).stream()
        .collect(Collectors.toMap(AssetLink::getLinkType, AssetLink::getTargetId));
  }

  /**
   * Return all {@link AssetLinkType#HAS_HUMAN_READABLE} rows from PostgreSQL.
   * Used by {@link eu.xfsc.fc.core.util.GraphRebuilder} to restore link triples after a graph
   * rebuild. Only MR→HR rows are returned; the inverse HR→MR rows are skipped because
   * {@link #writeLinkTriples} writes both directions from a single MR→HR pair.
   *
   * @return MR→HR link rows
   */
  @Transactional(readOnly = true)
  public List<AssetLink> getHumanReadableLinks() {
    return assetLinkRepository.findByLinkType(AssetLinkType.HAS_HUMAN_READABLE);
  }

  /**
   * Write {@code fcmeta:hasHumanReadable} and {@code fcmeta:hasMachineReadable} triples
   * to the graph DB for the given MR–HR pair.
   *
   * <p><strong>Angle-bracket format:</strong> {@link CredentialClaim#getSubjectValue()}
   * strips the surrounding {@code <} and {@code >} unconditionally. All IRIs passed
   * to {@code CredentialClaim} must therefore be wrapped: {@code "<" + iri + ">"}.</p>
   *
   * @param mrIri IRI of the machine-readable asset
   * @param hrIri IRI of the human-readable asset
   */
  public void writeLinkTriples(String mrIri, String hrIri) {
    final var ns = namespaceProperties.getNamespace();

    final var hasHumanReadable = new CredentialClaim(
        "<" + mrIri + ">",
        "<" + ns + PREDICATE_HAS_HUMAN_READABLE + ">",
        "<" + hrIri + ">");
    final var hasMachineReadable = new CredentialClaim(
        "<" + hrIri + ">",
        "<" + ns + PREDICATE_HAS_MACHINE_READABLE + ">",
        "<" + mrIri + ">");

    graphDb.addClaims(List.of(hasHumanReadable), mrIri);
    graphDb.addClaims(List.of(hasMachineReadable), hrIri);
  }

  // --- Private helpers ---

  private AssetLink buildLink(String sourceId, String targetId, AssetLinkType linkType, String createdBy) {
    final var link = new AssetLink();
    link.setSourceId(sourceId);
    link.setTargetId(targetId);
    link.setLinkType(linkType);
    link.setCreatedBy(createdBy);
    return link;
  }

  private AssetLinkType invertLinkType(AssetLinkType linkType) {
    return switch (linkType) {
      case HAS_HUMAN_READABLE -> AssetLinkType.HAS_MACHINE_READABLE;
      case HAS_MACHINE_READABLE -> AssetLinkType.HAS_HUMAN_READABLE;
    };
  }
}
