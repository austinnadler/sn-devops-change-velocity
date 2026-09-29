package io.rapdev.demo.greeting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GreetingController.class)
@Import(GreetingService.class)
class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsDefaultGreeting() throws Exception {
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, World!"));
    }

    @Test
    void returnsNamedGreeting() throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", "Austin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, Austin!"));
    }

    @Test
    void returnsBadRequestForLongName() throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", "a".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
