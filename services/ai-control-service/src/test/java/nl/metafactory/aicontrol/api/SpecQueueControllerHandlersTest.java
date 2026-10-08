package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.SpecQueueSettingsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class SpecQueueControllerHandlersTest {

    private final SpecQueueController controller = new SpecQueueController(null);

    private static MethodArgumentNotValidException invalid(BeanPropertyBindingResult result) throws Exception {
        var method = SpecQueueControllerHandlersTest.class.getDeclaredMethod("target", SpecQueueSettingsRequest.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), result);
    }

    @SuppressWarnings("unused")
    private void target(SpecQueueSettingsRequest request) {
    }

    @Test
    void validationMessageListsFieldErrorsThenGlobalErrorsWithoutValues() throws Exception {
        var result = new BeanPropertyBindingResult(new SpecQueueSettingsRequest(null), "request");
        result.addError(new FieldError("request", "autoMergeAllowed", "secret-rejected-value", false, null, null, "must not be null"));
        result.addError(new ObjectError("request", "global rule broken"));

        var response = controller.handleValidation(invalid(result));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message())
                .isEqualTo("Request validation failed: autoMergeAllowed: must not be null; global rule broken")
                .doesNotContain("secret-rejected-value");
    }

    @Test
    void validationMessageIsCutAtFiveHundredCharacters() throws Exception {
        var result = new BeanPropertyBindingResult(new SpecQueueSettingsRequest(null), "request");
        result.addError(new ObjectError("request", "x".repeat(900)));

        assertThat(controller.handleValidation(invalid(result)).getBody().message()).hasSize(500);
    }
}
