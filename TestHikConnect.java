import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

public class TestHikConnect {
    public static void main(String[] args) throws Exception {
        String baseUrl = "https://isgp.hikcentralconnect.com";
        String appKey = "R4uf4WQRgFctNtXWEItFIDZcH0Qs75xZ";
        String secretKey = "tM09InjSz2c4PneL1stw2U7iUIZdDXBo";

        HttpClient client = HttpClient.newHttpClient();

        // 1. Get Token
        String tokenBody = String.format("{\"appKey\":\"%s\",\"secretKey\":\"%s\"}", appKey, secretKey);
        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/hccgw/platform/v1/token/get"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                .build();
        
        HttpResponse<String> tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        
        String token = tokenResp.body().split("\"accessToken\":\"")[1].split("\"")[0];

        // 2. Get 1 Event
        String beginTime = LocalDateTime.now().minusDays(2).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        
        String searchBody = String.format("{\"pageIndex\":1,\"pageSize\":1,\"searchCriteria\":{\"beginTime\":\"%s\",\"endTime\":\"%s\"}}", beginTime, endTime);
        
        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/hccgw/acs/v1/event/certificaterecords/search"))
                .header("Content-Type", "application/json")
                .header("Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(searchBody))
                .build();
                
        HttpResponse<String> searchResp = client.send(searchReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Event Resp: " + searchResp.body());
    }
}
