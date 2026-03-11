package federatedcatalogue;

import static federatedcatalogue.AssetSigner.signAsset;
import static federatedcatalogue.SimulationHelper.addRandomId;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import federatedcatalogue.common.CommonSimulation;
import io.gatling.javaapi.core.*;
import java.time.Duration;

public class AssetSimulation extends CommonSimulation {
  private static final String USERS_NUMBER_PARAM = "fc-performance-testing.asset.users";
  private static final String USERS_NUMBER_RAMP_TIME_PARAM = "fc-performance-testing.asset.rampTime";
  private static final String DURING_TIME_PARAM = "fc-performance-testing.asset.duringTime";

  ChainBuilder get = exec(http("Get asset")
      .get(session -> "/assets/" + session.get(ASSET_HASH_PARAM))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token").toString())
      .check(status().is(200)));

  ChainBuilder search = exec(http("Search asset")
      .get(session -> "/assets?statuses=ACTIVE,DEPRECATED,REVOKED,EOL&hashes=" + session.get(ASSET_HASH_PARAM))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ChainBuilder add = exec(http("Add asset")
      .post("/assets")
      .body(StringBody(session -> signAsset(addRandomId(getAssetContent()))))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().saveAs(IS_ADDED_PARAM))
      .check(status().is(201))
      .check(jsonPath("$..assetHash").saveAs(ASSET_HASH_PARAM)));

  ChainBuilder revoke = exec(http("Revoke asset")
      .post(session -> "/assets/" + session.get(ASSET_HASH_PARAM).toString() + "/revoke")
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ChainBuilder delete = exec(http("Delete asset")
      .delete(session -> "/assets/" + session.get(ASSET_HASH_PARAM).toString())
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ScenarioBuilder manipulateAssets = scenario("Manipulate assets")
      .exec(getAccessToken("catalog-admin", "catalog-admin"))
      .pause(Duration.ofMillis(100))
      .during(Duration.ofSeconds(Integer.parseInt(System.getProperty(DURING_TIME_PARAM)))).on(
          exec(add)
              .pause(Duration.ofMillis(100))
              .doIf(session -> session.get(IS_ADDED_PARAM).toString().equals("201"))
              .then(exec(get).pause(Duration.ofMillis(100))
                  .exec(search)
                  .pause(Duration.ofMillis(100))
                  .exec(revoke)
                  .pause(Duration.ofMillis(100))
                  .exec(delete)
                  .pause(Duration.ofMillis(100))));

  {
    setUp(
        manipulateAssets.injectOpen(rampUsers(Integer.parseInt(System.getProperty(USERS_NUMBER_PARAM)))
                .during(Integer.parseInt(System.getProperty(USERS_NUMBER_RAMP_TIME_PARAM))))
            .protocols(getHttpProtocol()));
  }
}
