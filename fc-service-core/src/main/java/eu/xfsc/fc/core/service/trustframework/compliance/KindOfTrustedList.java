package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Classifies the trusted-list mechanism used by a trust framework profile
 * to establish membership or attestation anchors.
 */
public enum KindOfTrustedList {
  GAIAX_IPFS_ETSI,
  GAIAX_TRUSTED_LIST_REST,
  TRAIN,
  EBSI,
  OTHER
}
