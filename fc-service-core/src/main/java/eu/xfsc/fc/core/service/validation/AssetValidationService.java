package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.SingleAssetValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;

/**
 * On-demand validation of stored assets against stored schemas.
 *
 * <p>Results are persisted via {@link ValidationResultStore} and retrievable
 * through the GET /assets/{id}/validations endpoint.</p>
 */
public interface AssetValidationService {

  /**
   * Validates a single stored asset against SHACL, JSON Schema, or XML Schema.
   *
   * <p>The schema type is determined by the asset's content type. If schemaIds are
   * provided, those schemas are used; if validateAgainstAllSchemas=true, the composite
   * schema (all schemas of the matching type) is used.</p>
   *
   * @param assetId the IRI of the asset to validate
   * @param request optional validation parameters (schemaIds, validateAgainstAllSchemas)
   * @return validation response with result ID and optional report
   * @throws eu.xfsc.fc.core.exception.NotFoundException if the asset or a schema ID does not exist
   * @throws eu.xfsc.fc.core.exception.VerificationException if the asset type is not validatable
   */
  ValidationResponse validateAsset(String assetId, SingleAssetValidationRequest request);

  /**
   * Validates multiple RDF assets combined into a single data graph against SHACL shapes.
   *
   * <p>All listed assets must be RDF assets. Their content is merged into a single
   * Jena Model before SHACL validation runs. Maximum 20 assets per request.</p>
   *
   * @param request the asset IRIs and schema selection (schemaIds or validateAgainstAllSchemas)
   * @return validation response with result ID and optional report
   * @throws eu.xfsc.fc.core.exception.NotFoundException if an asset or schema ID does not exist
   * @throws eu.xfsc.fc.core.exception.VerificationException if any asset is not an RDF asset
   */
  ValidationResponse validateAssets(ValidationRequest request);
}
