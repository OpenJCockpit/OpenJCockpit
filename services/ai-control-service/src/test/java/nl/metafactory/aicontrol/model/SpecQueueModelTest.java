package nl.metafactory.aicontrol.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SpecQueueModelTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static List<String> names(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toList();
    }

    /** Reads the enum values of a top-level schema out of the contract, keeping model and contract in sync. */
    private static List<String> contractEnum(String file, String schema) throws Exception {
        String yaml = Files.readString(Path.of("../../shared/contracts/schemas/" + file));
        String block = yaml.split("(?m)^" + schema + ":\\n", 2)[1].split("(?m)^\\S", 2)[0];
        var m = Pattern.compile("(?m)^    - (\\w+)$").matcher(block);
        return m.results().map(r -> r.group(1)).collect(Collectors.toList());
    }

    @Test
    void enumsMatchTheContract() throws Exception {
        assertThat(names(SpecQueueState.class)).isEqualTo(contractEnum("spec-queue.yaml", "SpecQueueState"));
        assertThat(names(SpecQueueItemStatus.class)).isEqualTo(contractEnum("spec-queue.yaml", "SpecQueueItemStatus"));
        assertThat(names(SpecQueueMergeState.class)).isEqualTo(contractEnum("spec-queue.yaml", "SpecQueueMergeState"));
        assertThat(names(SpecQueueFailureReason.class)).isEqualTo(contractEnum("spec-queue.yaml", "SpecQueueFailureReason"));
        assertThat(names(GitWorkspaceJobErrorCode.class)).isEqualTo(contractEnum("workflow.yaml", "GitWorkspaceJobErrorCode"));
    }

    @Test
    void statusGroupings() {
        for (SpecQueueItemStatus s : SpecQueueItemStatus.values()) {
            boolean failed = s == SpecQueueItemStatus.FAILED;
            assertThat(java.util.stream.Stream.of(s.isOpen(), s.isTerminal(), failed).filter(b -> b).count())
                    .as(s.name()).isEqualTo(1);
            assertThat(s.isActive()).isEqualTo(s.isOpen() && s != SpecQueueItemStatus.QUEUED);
        }
    }

    @Test
    void requestValidation() {
        assertThat(VALIDATOR.validate(new SpecQueueEnqueueRequest("a".repeat(500), "w".repeat(200), false))).isEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueEnqueueRequest(" ", "w", false))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueEnqueueRequest("a".repeat(501), "w", false))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueItemUpdateRequest(null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueItemUpdateRequest(null, false))).isEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueItemUpdateRequest("", null))).isNotEmpty();
        UUID id = UUID.randomUUID();
        assertThat(VALIDATOR.validate(new SpecQueueOrderRequest(List.of(id, UUID.randomUUID())))).isEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueOrderRequest(List.of(id, id)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueOrderRequest(List.of()))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueOrderRequest(null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SpecQueueSettingsRequest(null))).isNotEmpty();
    }

    @Test
    void validationHelpersAreNotSerialized() {
        var mapper = JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(new SpecQueueItemUpdateRequest("w", null)))
                .doesNotContain("atLeastOnePropertyPresent");
        assertThat(mapper.writeValueAsString(new SpecQueueOrderRequest(List.of(UUID.randomUUID()))))
                .doesNotContain("itemIdsUnique");
    }
}
