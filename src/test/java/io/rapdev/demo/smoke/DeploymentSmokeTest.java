package io.rapdev.demo.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Post-deployment smoke tests. Run against a deployed environment:
 *   mvn test -Psmoke -Dsmoke.baseUrl=https://app.example.com
 * Excluded from the default (unit test) build via the "smoke" tag.
 */
@Tag("smoke")
class DeploymentSmokeTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String baseUrl;

    @BeforeAll
    static void resolveBaseUrl() {
        baseUrl = System.getProperty("smoke.baseUrl", "");
        assumeTrue(!baseUrl.isBlank() && !baseUrl.startsWith("${"), "smoke.baseUrl not set; skipping smoke tests");
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    @Test
    void healthIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health/readiness");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void infoExposesBuildVersion() throws Exception {
        HttpResponse<String> response = get("/actuator/info");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"version\"");
    }

    @Test
    void greetingEndpointResponds() throws Exception {
        HttpResponse<String> response = get("/api/greeting?name=Smoke");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Hello, Smoke!");
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
