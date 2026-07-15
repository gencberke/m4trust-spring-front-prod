package com.m4trust.coreapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.m4trust.coreapi.api.EchoController;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EchoController.class)
class EchoValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankMessageReturnsProblemDetailsWith422AndCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(header().string("X-Correlation-ID", Matchers.notNullValue()));
    }

    @Test
    void suppliedCorrelationIdIsEchoedInResponseHeaderAndBody() throws Exception {
        String correlationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        mockMvc.perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}")
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(header().string("X-Correlation-ID", correlationId));
    }
}
