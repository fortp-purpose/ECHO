
package echo.platform.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ApiExceptionHandlerTest {

    @Test
    void invalidRequestReturnsStandardValidationError() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ValidationTestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("One or more fields failed validation."))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.correlationId").value("unavailable"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class ValidationTestController {

        @PostMapping("/test/validation")
        String validate(@Valid @RequestBody TestRequest request) {
            return "OK";
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
