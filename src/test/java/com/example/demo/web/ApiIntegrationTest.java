package com.example.demo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests: the real application (controllers, validation, services, JPA, H2) is started on a
 * random port and called over HTTP with the JDK's own HttpClient. Each test creates its own data so
 * the tests do not depend on each other or on the seeded menu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {

    private static final Pattern FIRST_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Value("${local.server.port}")
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    // ---------- helpers ----------

    private HttpResponse<String> call(String method, String path, String json) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static long firstId(String json) {
        Matcher m = FIRST_ID.matcher(json);
        assertThat(m.find()).as("an id in " + json).isTrue();
        return Long.parseLong(m.group(1));
    }

    private long createFood(String name, String price, boolean available) throws Exception {
        HttpResponse<String> response = call("POST", "/api/foods",
                "{\"name\":\"%s\",\"category\":\"Test\",\"price\":%s,\"available\":%s}".formatted(name, price, available));
        assertThat(response.statusCode()).isEqualTo(201);
        return firstId(response.body());
    }

    private HttpResponse<String> order(long foodId, int quantity) throws Exception {
        return call("POST", "/api/orders",
                "{\"customerName\":\"Tester\",\"items\":[{\"foodId\":%d,\"quantity\":%d}]}".formatted(foodId, quantity));
    }

    // ---------- tests ----------

    @Test
    void seededMenuIsListed() throws Exception {
        HttpResponse<String> response = call("GET", "/api/foods", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Margherita Pizza");
    }

    @Test
    void billingRulesAreExposedToTheFrontEnd() throws Exception {
        HttpResponse<String> response = call("GET", "/api/billing/rules", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("taxRate", "discountThreshold", "discountRate");
    }

    @Test
    void placingAnOrderReturnsTheCalculatedBill() throws Exception {
        long foodId = createFood("Bill Test Dish", "200.00", true);

        HttpResponse<String> response = order(foodId, 3); // subtotal 600 -> tax 30 -> discount 60 -> total 570

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).containsPattern("\"subtotal\"\\s*:\\s*600(\\.0+)?");
        assertThat(response.body()).containsPattern("\"tax\"\\s*:\\s*30(\\.0+)?");
        assertThat(response.body()).containsPattern("\"discount\"\\s*:\\s*60(\\.0+)?");
        assertThat(response.body()).containsPattern("\"total\"\\s*:\\s*570(\\.0+)?");
        assertThat(response.body()).contains("\"status\":\"PLACED\"");
    }

    @Test
    void orderWithoutItemsIsRejectedWithAClearMessage() throws Exception {
        HttpResponse<String> response =
                call("POST", "/api/orders", "{\"customerName\":\"Tester\",\"items\":[]}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"status\":400").contains("items");
    }

    @Test
    void invalidMenuItemIsRejected() throws Exception {
        HttpResponse<String> response =
                call("POST", "/api/foods", "{\"name\":\"\",\"price\":-5}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("name").contains("price");
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        HttpResponse<String> response = call("POST", "/api/foods", "{not json");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void unavailableDishCannotBeOrdered() throws Exception {
        long foodId = createFood("Sold Out Dish", "50.00", false);

        HttpResponse<String> response = order(foodId, 1);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("unavailable");
    }

    @Test
    void unknownDishIsReportedAs404() throws Exception {
        HttpResponse<String> response = order(987654321L, 1);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void orderFollowsItsLifeCycle() throws Exception {
        long foodId = createFood("Life Cycle Dish", "100.00", true);
        long orderId = firstId(order(foodId, 1).body());
        String statusPath = "/api/orders/" + orderId + "/status";

        assertThat(call("PUT", statusPath, "{\"status\":\"PREPARING\"}").statusCode()).isEqualTo(200);
        // skipping READY is not allowed
        assertThat(call("PUT", statusPath, "{\"status\":\"DELIVERED\"}").statusCode()).isEqualTo(409);
        assertThat(call("PUT", statusPath, "{\"status\":\"READY\"}").statusCode()).isEqualTo(200);
        assertThat(call("PUT", statusPath, "{\"status\":\"DELIVERED\"}").statusCode()).isEqualTo(200);
        // finished orders are frozen
        assertThat(call("PUT", statusPath, "{\"status\":\"CANCELLED\"}").statusCode()).isEqualTo(409);
    }

    @Test
    void unknownStatusValueIsRejected() throws Exception {
        long foodId = createFood("Status Dish", "10.00", true);
        long orderId = firstId(order(foodId, 1).body());

        HttpResponse<String> response =
                call("PUT", "/api/orders/" + orderId + "/status", "{\"status\":\"FLYING\"}");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void ordersCanBeFilteredByStatus() throws Exception {
        long foodId = createFood("Filter Dish", "10.00", true);
        String uniqueCustomer = "Filter-" + System.nanoTime();
        HttpResponse<String> created = call("POST", "/api/orders",
                "{\"customerName\":\"%s\",\"items\":[{\"foodId\":%d,\"quantity\":1}]}".formatted(uniqueCustomer, foodId));
        assertThat(created.statusCode()).isEqualTo(201);

        HttpResponse<String> placed = call("GET", "/api/orders?status=PLACED", null);
        HttpResponse<String> delivered = call("GET", "/api/orders?status=DELIVERED", null);

        assertThat(placed.statusCode()).isEqualTo(200);
        assertThat(placed.body()).contains(uniqueCustomer);
        assertThat(delivered.body()).doesNotContain(uniqueCustomer);
        assertThat(call("GET", "/api/orders?status=FLYING", null).statusCode()).isEqualTo(400);
    }

    @Test
    void menuItemCanBeUpdatedAndDeleted() throws Exception {
        long foodId = createFood("Editable Dish", "10.00", true);

        HttpResponse<String> updated = call("PUT", "/api/foods/" + foodId,
                "{\"name\":\"Edited Dish\",\"category\":\"Test\",\"price\":12.50,\"available\":true}");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("Edited Dish");

        assertThat(call("DELETE", "/api/foods/" + foodId, null).statusCode()).isEqualTo(204);
        assertThat(call("GET", "/api/foods/" + foodId, null).statusCode()).isEqualTo(404);
    }

    @Test
    void unknownUrlsReturn404NotAServerError() throws Exception {
        HttpResponse<String> response = call("GET", "/api/does-not-exist", null);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        HttpResponse<String> response = call("GET", "/actuator/health", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }
}
