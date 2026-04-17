package eu.xfsc.fc.core.pojo;

/**
 * Defines the supported link directions between assets in the catalogue.
 *
 * <p>Each bidirectional link between a machine-readable asset and a human-readable
 * representation is stored as two rows: one with {@code HAS_HUMAN_READABLE} (MR → HR)
 * and one with {@code HAS_MACHINE_READABLE} (HR → MR).</p>
 */
public enum AssetLinkType {
  /** Source is a machine-readable asset; target is a human-readable representation. */
  HAS_HUMAN_READABLE,
  /** Source is a human-readable representation; target is the machine-readable asset. */
  HAS_MACHINE_READABLE
}
