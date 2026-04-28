package eu.xfsc.fc.core.dao.validation;

/**
 * Validator type discriminator values for {@link ValidationResult#getValidatorType()}.
 */
public enum ValidatorType {

  /** On-demand SHACL validation of RDF assets (Cases A, B, C). */
  SHACL,

  /** On-demand JSON Schema validation of non-RDF JSON assets (Case D). */
  JSON_SCHEMA,

  /** On-demand XML Schema validation of non-RDF XML assets (Case E). */
  XML_SCHEMA,

  /** External trust framework compliance check. */
  TRUST_FRAMEWORK
}
