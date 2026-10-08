package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.DataClassification;
import nl.metafactory.aicontrol.model.ModelRouteRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRoutingServiceTest {

    private final ModelRoutingService service = new ModelRoutingService();

    @Test
    void l0PublicRoutesToCloud() {
        var decision = service.decide(new ModelRouteRequest("c", "s", DataClassification.L0_PUBLIC, "gen"));
        assertThat(decision.route()).isEqualTo("CLOUD");
        assertThat(decision.cloudAllowed()).isTrue();
    }

    @Test
    void l1InternalRoutesToHybrid() {
        var decision = service.decide(new ModelRouteRequest("c", "s", DataClassification.L1_INTERNAL, "gen"));
        assertThat(decision.route()).isEqualTo("HYBRID");
        assertThat(decision.cloudAllowed()).isTrue();
    }

    @Test
    void l2ConfidentialRoutesToLocal() {
        var decision = service.decide(new ModelRouteRequest("c", "s", DataClassification.L2_CONFIDENTIAL, "gen"));
        assertThat(decision.route()).isEqualTo("LOCAL");
        assertThat(decision.cloudAllowed()).isFalse();
    }

    @Test
    void l3PersonalRoutesToLocalPrivate() {
        var decision = service.decide(new ModelRouteRequest("c", "s", DataClassification.L3_PERSONAL, "gen"));
        assertThat(decision.route()).isEqualTo("LOCAL_PRIVATE");
        assertThat(decision.cloudAllowed()).isFalse();
    }

    @Test
    void l4SecretIsBlocked() {
        var decision = service.decide(new ModelRouteRequest("c", "s", DataClassification.L4_SECRET, "gen"));
        assertThat(decision.route()).isEqualTo("BLOCKED");
        assertThat(decision.cloudAllowed()).isFalse();
    }

    @Test
    void nullClassificationDefaultsToL1() {
        var decision = service.decide(new ModelRouteRequest("c", "s", null, "gen"));
        assertThat(decision.route()).isEqualTo("HYBRID");
    }
}
