package eu.xfsc.fc.server.service;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.AssetTypeConfig;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.service.assettypes.AssetTypeRestrictionService;
import eu.xfsc.fc.server.generated.controller.AssetTypeAdminApiDelegate;
import lombok.RequiredArgsConstructor;

/**
 * Service for asset type administration endpoints.
 */
@Service
@RequiredArgsConstructor
public class AssetTypeAdminService implements AssetTypeAdminApiDelegate {

  private final AssetTypeRestrictionService restrictionService;
  private final AssetDao assetDao;

  @Override
  public ResponseEntity<AssetTypeConfig> getAssetTypeConfig() {
    AssetTypeConfig config = new AssetTypeConfig();
    config.setEnabled(restrictionService.isRestrictionEnabled());
    config.setAllowedTypes(restrictionService.getAllowedTypes());
    return ResponseEntity.ok(config);
  }

  @Override
  public ResponseEntity<Void> updateAssetTypeConfig(AssetTypeConfig config) {
    restrictionService.setConfig(
        Boolean.TRUE.equals(config.getEnabled()),
        config.getAllowedTypes() != null ? config.getAllowedTypes() : Collections.emptyList());
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<List<String>> getExistingAssetTypes() {
    return ResponseEntity.ok(assetDao.selectDistinctCredentialTypes());
  }
}
