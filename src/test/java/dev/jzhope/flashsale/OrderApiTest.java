package dev.jzhope.flashsale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import dev.jzhope.flashsale.api.ApiException;
import dev.jzhope.flashsale.auth.AuthService;
import dev.jzhope.flashsale.order.RateLimiter;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate db;
    @MockitoBean RateLimiter limiter;
    @Test void validatesAndEnforcesAdmission() throws Exception {
        String token=auth.register("api_buyer","student-password").accessToken();
        String body="{\"productId\":1,\"quantity\":0}";
        mvc.perform(post("/api/orders").header("Authorization","Bearer "+token).header("Idempotency-Key","key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isBadRequest());
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts")).when(limiter).check(anyString());
        mvc.perform(post("/api/orders").header("Authorization","Bearer "+token).header("Idempotency-Key","key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isTooManyRequests());
    }
}
