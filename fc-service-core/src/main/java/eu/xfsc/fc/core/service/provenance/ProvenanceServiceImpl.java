package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceCredentials;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult.IssuerResolutionStatusEnum;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.provenance.ProvenanceCredentialRepository;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business-logic implementation for provenance credential operations.
 *
 * <p>All mutating operations ({@link #add}, {@link #verifyOne}, {@link #verifyAll}) run in a
 * {@code REQUIRES_NEW} transaction so they remain isolated from the Envers audit session and
 * do not produce a new asset revision.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceServiceImpl implements ProvenanceService {

  private final VerificationService verificationService;
  private final ProvenanceCredentialRepository repository;
  private final AssetDao assetDao;
  private final GraphStore graphStore;
  private final ProtectedNamespaceFilter namespaceFilter;
  private final ProvenanceCredentialParser parser;
  private final ProvenanceModelMapper mapper;

  /**
   * {@inheritDoc}
   *
   * <p>Validates, stores, and optionally mirrors the provenance credential as a PROV-O triple.</p>
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ProvenanceCredential add(String assetId, Integer version, String rawVc, String format) {
    log.debug("add; assetId={}, version={}, format={}", assetId, version, format);

    int resolvedVersion = resolveVersion(assetId, version);
    String expectedSubjectId = assetId + ":v" + resolvedVersion;

    CredentialVerificationResult verificationResult = parser.parseAndValidateVc(rawVc, format);

    String subjectId = verificationResult.getId();
    if (!expectedSubjectId.equals(subjectId)) {
      throw new ClientException(
          "credentialSubject.id '" + subjectId + "' must equal '" + expectedSubjectId + "'");
    }

    ProvenanceInfo provenance = parser.extractProvenance(rawVc);
    String credentialId = parser.extractCredentialId(rawVc);

    if (credentialId != null && repository.existsByCredentialId(credentialId)) {
      throw new ConflictException("Provenance credential already exists: credentialId=" + credentialId);
    }

    List<CredentialClaim> provTriples = ProvOTripleBuilder.build(
        expectedSubjectId, provenance.type(), provenance.objectValue());
    FilteredClaims filtered = namespaceFilter.filterClaims(provTriples, "provenance add");
    if (filtered.hasWarning()) {
      throw new ClientException(
          "Provenance credential uses the protected CAT namespace. Accepted namespace: "
              + "http://www.w3.org/ns/prov#. Details: " + filtered.warning());
    }

    ProvenanceRecord entity = ProvenanceRecord.builder()
        .assetId(assetId)
        .assetVersion(resolvedVersion)
        .credentialId(credentialId)
        .issuer(verificationResult.getIssuer())
        .issuedAt(verificationResult.getIssuedDateTime())
        .provenanceType(provenance.type())
        .credentialContent(rawVc)
        .credentialFormat(parser.detectFormatLabel(rawVc))
        .build();

    entity = repository.save(entity);
    log.info("add; stored provenance credential id={} for asset={} version={}",
        entity.getId(), assetId, resolvedVersion);

    graphStore.addClaims(provTriples, expectedSubjectId);

    return mapper.toModel(entity);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public ProvenanceCredentials list(String assetId, Integer version, Pageable pageable) {
    log.debug("list; assetId={}, version={}", assetId, version);
    requireAssetExists(assetId);

    Page<ProvenanceRecord> page;
    if (version != null) {
      page = repository.findByAssetIdAndAssetVersionOrderByIssuedAtDesc(assetId, version, pageable);
    } else {
      page = repository.findByAssetIdOrderByIssuedAtDesc(assetId, pageable);
    }

    List<ProvenanceCredential> items = page.getContent().stream()
        .map(mapper::toModel)
        .toList();

    return new ProvenanceCredentials((int) page.getTotalElements(), items);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public ProvenanceCredential get(String assetId, String credentialId) {
    log.debug("get; assetId={}, credentialId={}", assetId, credentialId);
    return mapper.toModel(requireCredentialBelongsToAsset(assetId, credentialId));
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ProvenanceVerificationResult verifyOne(String assetId, String credentialId) {
    log.debug("verifyOne; assetId={}, credentialId={}", assetId, credentialId);
    ProvenanceRecord entity = requireCredentialBelongsToAsset(assetId, credentialId);

    ProvenanceVerificationResult result = verifyEntity(entity);
    persistVerificationResult(entity, result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ProvenanceVerificationResult verifyAll(String assetId, Integer version) {
    log.debug("verifyAll; assetId={}, version={}", assetId, version);
    requireAssetExists(assetId);

    List<ProvenanceRecord> entities;
    if (version != null) {
      entities = repository.findByAssetIdAndAssetVersionOrderByIssuedAtDesc(
          assetId, version, Pageable.unpaged()).getContent();
    } else {
      entities = repository.findByAssetIdOrderByIssuedAtDesc(assetId, Pageable.unpaged()).getContent();
    }

    boolean allValid = true;
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Instant latestTimestamp = null;

    for (ProvenanceRecord entity : entities) {
      ProvenanceVerificationResult singleResult = verifyEntity(entity);
      mapper.applyVerificationResult(entity, singleResult);

      if (Boolean.FALSE.equals(singleResult.getIsValid())) {
        allValid = false;
        if (singleResult.getErrors() != null) {
          singleResult.getErrors().forEach(e ->
              errors.add("[" + entity.getCredentialId() + "] " + e));
        }
      }
      if (singleResult.getWarnings() != null) {
        singleResult.getWarnings().forEach(w ->
            warnings.add("[" + entity.getCredentialId() + "] " + w));
      }
      Instant ts = singleResult.getVerificationTimestamp();
      if (ts != null && (latestTimestamp == null || ts.isAfter(latestTimestamp))) {
        latestTimestamp = ts;
      }
    }

    repository.saveAll(entities);

    return new ProvenanceVerificationResult()
        .isValid(allValid)
        .verificationTimestamp(latestTimestamp)
        .validatorDids(List.of())
        .errors(errors)
        .warnings(warnings);
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private int resolveVersion(String assetId, Integer requestedVersion) {
    if (requestedVersion != null) {
      Optional<AssetRecord> record = assetDao.selectVersion(assetId, requestedVersion);
      if (record.isEmpty()) {
        throw new NotFoundException("Asset not found: assetId=" + assetId + ", version=" + requestedVersion);
      }
      return requestedVersion;
    }
    int count = assetDao.getVersionCount(assetId);
    if (count == 0) {
      throw new NotFoundException("Asset not found: assetId=" + assetId);
    }
    return count;
  }

  private void requireAssetExists(String assetId) {
    if (assetDao.getVersionCount(assetId) == 0) {
      throw new NotFoundException("Asset not found: assetId=" + assetId);
    }
  }

  private ProvenanceRecord findByCredentialId(String credentialId) {
    return repository.findByCredentialId(credentialId)
        .orElseThrow(() -> new NotFoundException(
            "Provenance credential not found: credentialId=" + credentialId));
  }

  private ProvenanceRecord requireCredentialBelongsToAsset(String assetId, String credentialId) {
    requireAssetExists(assetId);
    ProvenanceRecord entity = findByCredentialId(credentialId);
    if (!entity.getAssetId().equals(assetId)) {
      throw new NotFoundException(
          "Provenance credential not found for asset: credentialId=" + credentialId);
    }
    return entity;
  }

  private ProvenanceVerificationResult verifyEntity(ProvenanceRecord entity) {
    Instant now = Instant.now();
    try {
      ContentAccessorDirect content = new ContentAccessorDirect(entity.getCredentialContent());
      CredentialVerificationResult result = verificationService.verifyCredential(content);

      return new ProvenanceVerificationResult()
          .isValid(true)
          .verificationTimestamp(now)
          .issuerResolutionStatus(IssuerResolutionStatusEnum.RESOLVED)
          .issuedDateTime(result.getIssuedDateTime())
          .signatureValid(true)
          .validatorDids(result.getValidatorDids() != null ? result.getValidatorDids() : List.of())
          .errors(List.of())
          .warnings(result.getWarnings() != null ? result.getWarnings() : List.of());

    } catch (VerificationException ex) {
      log.warn("verifyEntity; verification failed for credentialId={}: {}",
          entity.getCredentialId(), ex.getMessage());
      return new ProvenanceVerificationResult()
          .isValid(false)
          .verificationTimestamp(now)
          .issuerResolutionStatus(IssuerResolutionStatusEnum.UNRESOLVABLE)
          .signatureValid(false)
          .validatorDids(List.of())
          .errors(List.of(ex.getMessage()))
          .warnings(List.of());
    }
  }

  private void persistVerificationResult(ProvenanceRecord entity, ProvenanceVerificationResult result) {
    mapper.applyVerificationResult(entity, result);
    repository.save(entity);
  }
}
