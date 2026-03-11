package federatedcatalogue;

import java.util.UUID;

public class SimulationHelper {
  public static String getResourcePath() {
    return System.getProperty("user.dir") + "\\user-files\\resources\\";
  }

  public static String addRandomId(String asset) {
    String id = UUID.randomUUID().toString();
    return asset
        .replace("http://example.edu/verifiablePresentation/self-description",
            "http://example.edu/verifiablePresentation/self-description-" + id)
        .replace("https://www.example.org/mySoftwareOffering",
            "https://www.example.org/mySoftwareOffering-" + id);
  }
}
