import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TestHikConnectDD {
    public static void main(String[] args) throws Exception {
        String baseUrl = "https://isgp.hikcentralconnect.com";
        String appKey = "R4uf4WQRgFctNtXWEItFIDZcH0Qs75xZ";
        String secretKey = "tM09InjSz2c4PneL1stw2U7iUIZdDXBo";

        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format("{\"appKey\":\"%s\",\"secretKey\":\"%s\"}", appKey, secretKey);
        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/hccgw/platform/v1/token/get"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                .build();
        
        HttpResponse<String> tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        String token = tokenResp.body().split("\"accessToken\":\"")[1].split("\"")[0];
        
        System.out.println("Fetching devices...");
        String reqBody = "{\"pageIndex\":1,\"pageSize\":50}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/hccgw/resource/v1/devices/get"))
                .header("Content-Type", "application/json")
                .header("Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();
                
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("DD Devices Resp: " + resp.body());
    }
}
