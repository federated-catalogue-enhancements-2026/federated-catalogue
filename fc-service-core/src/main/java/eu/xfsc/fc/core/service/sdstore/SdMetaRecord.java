package eu.xfsc.fc.core.service.sdstore;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.VerificationResult;

/**
 * Database record for SdMetaData table.
 */
public class SdMetaRecord extends SelfDescriptionMetadata {

  private Instant expirationTime;
  private String contentType;
  private Long fileSize;
  private String originalFilename;

  public SdMetaRecord(String id, String issuer, List<Validator> validators, ContentAccessor contentAccessor, Instant expirationTime) {
    super(id, issuer, validators, contentAccessor);
    this.expirationTime = expirationTime;
  }

  public SdMetaRecord(ContentAccessor contentAccessor, VerificationResult verificationResult) {
    super(contentAccessor, verificationResult);
    final List<Validator> validators = verificationResult.getValidators();
    if (validators != null) {
      Validator minVal = validators.stream().min(new Validator.ExpirationComparator()).orElse(null);
      expirationTime = minVal == null ? null : minVal.getExpirationDate();
    }
  }

  public SdMetaRecord(String sdHash, String id, SelfDescriptionStatus status, String issuer, List<String> validatorDids,
      Instant uploadTime, Instant statusTime, ContentAccessor content, Instant expirationTime,
      String contentType, Long fileSize, String originalFilename) {
	super(sdHash, id, status, issuer, validatorDids, uploadTime, statusTime, content);
	this.expirationTime = expirationTime;
	this.contentType = contentType;
	this.fileSize = fileSize;
	this.originalFilename = originalFilename;
  }

  public String getContent() {
    final ContentAccessor selfDescription = super.getSelfDescription();
    if (selfDescription == null) {
      return null;
    }
    return selfDescription.getContentAsString();
  }

  public Instant getExpirationTime() {
	return this.expirationTime;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String[] getValidators() {
    final List<String> validatorDids = getValidatorDids();
    if (validatorDids == null) {
      return null;
    }
    return validatorDids.toArray(String[]::new);
  }


}
