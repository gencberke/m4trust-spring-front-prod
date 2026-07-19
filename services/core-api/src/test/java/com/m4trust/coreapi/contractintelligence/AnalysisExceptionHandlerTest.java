package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.m4trust.coreapi.api.FieldValidationError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisExceptionHandlerTest {
    private final AnalysisExceptionHandler handler = new AnalysisExceptionHandler();

    @Test
    void validationUsesTheContractErrorsArrayShape() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/deals/id/extraction-review/accept");
        var response = handler.validation(new AnalysisExceptions.Validation("decisions[0].structuredValue.basisPoints"), request);

        assertEquals(422, response.getStatusCode().value());
        assertFalse(response.getBody().getProperties().containsKey("fieldErrors"));
        Object errors = response.getBody().getProperties().get("errors");
        List<?> entries = assertInstanceOf(List.class, errors);
        FieldValidationError error = assertInstanceOf(FieldValidationError.class, entries.getFirst());
        assertEquals("decisions[0].structuredValue.basisPoints", error.field());
        assertEquals("INVALID", error.code());
        assertEquals("The value is invalid.", error.message());
    }

    @Test
    void validationSerializesAsTheExactOpenApiErrorsArray() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ValidationProbe())
                .setControllerAdvice(handler).build();

        mvc.perform(post("/probe"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("decisions[0].category"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID"))
                .andExpect(jsonPath("$.errors[0].message").value("The value is invalid."))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Controller
    static class ValidationProbe {
        @PostMapping("/probe")
        void probe() {
            throw new AnalysisExceptions.Validation("decisions[0].category");
        }
    }
}
