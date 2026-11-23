package dev.jzhope.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.exception.ApiException;
import dev.jzhope.flashsale.service.AuthService;
import dev.jzhope.flashsale.service.RateLimiter;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @MockitoBean RateLimiter limiter;

  @Test
  void dtoRefactorPreservesOrderJsonAndStateTransitions() throws Exception {
    db.update(
        "INSERT INTO products(id,name,price_cents,stock) VALUES (17,'Contract Product',100,5)");
    String token = auth.register("contract_buyer", "student-password").accessToken();
    String response =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "contract")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"productId\":17,\"quantity\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalCents").value(200))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var order = json.readTree(response);
    var fields = new HashSet<String>();
    order.fieldNames().forEachRemaining(fields::add);
    assertThat(fields)
        .containsExactlyInAnyOrder(
            "id",
            "userId",
            "productId",
            "quantity",
            "totalCents",
            "status",
            "createdAt",
            "expiresAt");
    mvc.perform(
            post("/api/orders/" + order.get("id").asText() + "/simulate-payment")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  void validatesAndEnforcesAdmission() throws Exception {
    String token = auth.register("api_buyer", "student-password").accessToken();
    String body = "{\"productId\":1,\"quantity\":0}";
    mvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":1}"))
        .andExpect(status().isBadRequest());
    doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts"))
        .when(limiter)
        .check(anyString());
    mvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":1}"))
        .andExpect(status().isTooManyRequests());
  }
}
