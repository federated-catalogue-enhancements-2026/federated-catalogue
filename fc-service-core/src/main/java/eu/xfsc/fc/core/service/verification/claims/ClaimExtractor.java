package eu.xfsc.fc.core.service.verification.claims;

import java.util.List;

import eu.xfsc.fc.core.pojo.AssetClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;

public interface ClaimExtractor {

    List<AssetClaim> extractClaims(ContentAccessor content) throws Exception;
    
}
