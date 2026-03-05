package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;
import static eu.xfsc.fc.server.util.SessionUtils.getSessionParticipantId;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.xfsc.fc.api.generated.model.SelfDescription;
import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.sdstore.RdfDetector;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import lombok.extern.slf4j.Slf4j;

/**
 * Manual controller for non-RDF asset uploads via multipart/form-data and
 * application/octet-stream. These content types are not handled by the
 * generated delegate pattern (which only supports application/json).
 */
@Slf4j
@RestController
@RequestMapping("${openapi.eclipseXFSCFederatedCatalogue.base-path:}")
public class AssetUploadController {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    @Autowired
    private VerificationService verificationService;

    @Autowired
    private SelfDescriptionStore sdStorePublisher;

    @Autowired
    private RdfDetector rdfDetector;

    @PostMapping(value = "/self-descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<SelfDescription> addAssetMultipart(
            @RequestPart("file") MultipartFile file) {
        log.debug("addAssetMultipart.enter; filename: {}, contentType: {}, size: {}",
                file.getOriginalFilename(), file.getContentType(), file.getSize());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ServerException("Failed to read uploaded file", ex);
        }

        String contentType = file.getContentType() != null ? file.getContentType() : DEFAULT_CONTENT_TYPE;
        return processUpload(content, contentType, file.getOriginalFilename());
    }

    @PostMapping(value = "/self-descriptions", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<SelfDescription> addAssetOctetStream(
            @RequestBody byte[] content,
            @RequestHeader(value = "Content-Type", defaultValue = DEFAULT_CONTENT_TYPE) String contentType) {
        log.debug("addAssetOctetStream.enter; contentType: {}, size: {}", contentType, content.length);

        return processUpload(content, contentType, null);
    }

    private ResponseEntity<SelfDescription> processUpload(byte[] content, String contentType, String originalFilename) {
        if (content == null || content.length == 0) {
            throw new ClientException("Upload content must not be empty");
        }

        if (rdfDetector.isRdf(contentType, content)) {
            return handleRdfUpload(content, contentType);
        }
        return handleNonRdfUpload(content, contentType, originalFilename);
    }

    private ResponseEntity<SelfDescription> handleRdfUpload(byte[] content, String contentType) {
        log.debug("handleRdfUpload; detected RDF content, delegating to verification pipeline");
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        ContentAccessorDirect contentAccessor = new ContentAccessorDirect(text);

        VerificationResult verificationResult = verificationService.verifySelfDescription(contentAccessor);

        SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(verificationResult.getId(),
                verificationResult.getIssuer(), verificationResult.getValidators(), contentAccessor);
        sdMetadata.setContentType(contentType);
        sdMetadata.setFileSize((long) content.length);

        checkParticipantAccess(sdMetadata.getIssuer());
        sdStorePublisher.storeSelfDescription(sdMetadata, verificationResult);

        log.debug("handleRdfUpload.exit; stored RDF SD with hash: {}", sdMetadata.getSdHash());
        return ResponseEntity.created(URI.create("/self-descriptions/" + sdMetadata.getSdHash())).body(sdMetadata);
    }

    private ResponseEntity<SelfDescription> handleNonRdfUpload(byte[] content, String contentType,
                                                               String originalFilename) {
        log.debug("handleNonRdfUpload; non-RDF asset, skipping verification");
        String sdHash = calculateSha256AsHex(content);
        String participantId = getSessionParticipantId();
        Instant now = Instant.now();

        ContentAccessorBinary contentAccessor = new ContentAccessorBinary(content);
        SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(sdHash, null, SelfDescriptionStatus.ACTIVE,
                participantId, null, now, now, contentAccessor);
        sdMetadata.setContentType(contentType);
        sdMetadata.setFileSize((long) content.length);

        sdStorePublisher.storeAsset(sdMetadata, originalFilename);

        log.debug("handleNonRdfUpload.exit; stored asset with hash: {}", sdHash);
        return ResponseEntity.created(URI.create("/self-descriptions/" + sdHash)).body(sdMetadata);
    }
}