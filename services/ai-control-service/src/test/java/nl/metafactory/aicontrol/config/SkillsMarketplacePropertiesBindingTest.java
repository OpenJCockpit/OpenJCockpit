package nl.metafactory.aicontrol.config;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-57: the shipped {@code src/test/resources/application.yml} (which mirrors the shape of the
 * production {@code application.yml}) must bind {@code allowPrivateAddresses = false} — the deny
 * default — without any test needing to override it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class SkillsMarketplacePropertiesBindingTest {

    @Autowired
    SkillsMarketplaceProperties properties;

    @MockitoBean
    EmbabelAgentClient embabelAgentClient;

    @Test
    void shippedApplicationYmlBindsDenyByDefaultAndTheDocumentedTimeouts() {
        assertThat(properties.getEgress().isAllowPrivateAddresses()).isFalse();
        assertThat(properties.getRequestTimeoutSeconds()).isEqualTo(5);
        assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(2);
        assertThat(properties.getMaxResponseBytes()).isEqualTo(262_144L);
        assertThat(properties.getMaxItems()).isEqualTo(500);
        assertThat(properties.getMaxRedirects()).isEqualTo(1);
        assertThat(properties.getMaxConnectionsPerFetch()).isEqualTo(25);
    }

    @Test
    void fieldInitialisersDefaultToDenyEvenWithoutAnyBinding() {
        var fresh = new SkillsMarketplaceProperties();

        assertThat(fresh.getEgress().isAllowPrivateAddresses()).isFalse();
    }

    @Test
    void everySetterChangesTheCorrespondingGetterBehaviourally() {
        var fresh = new SkillsMarketplaceProperties();

        fresh.setRequestTimeoutSeconds(9);
        fresh.setConnectTimeoutSeconds(4);
        fresh.setMaxResponseBytes(1024L);
        fresh.setMaxItems(10);
        fresh.setMaxRedirects(3);
        fresh.setMaxConnectionsPerFetch(2);
        var egress = new SkillsMarketplaceProperties.Egress();
        egress.setAllowPrivateAddresses(true);
        fresh.setEgress(egress);

        assertThat(fresh.getRequestTimeoutSeconds()).isEqualTo(9);
        assertThat(fresh.getConnectTimeoutSeconds()).isEqualTo(4);
        assertThat(fresh.getMaxResponseBytes()).isEqualTo(1024L);
        assertThat(fresh.getMaxItems()).isEqualTo(10);
        assertThat(fresh.getMaxRedirects()).isEqualTo(3);
        assertThat(fresh.getMaxConnectionsPerFetch()).isEqualTo(2);
        assertThat(fresh.getEgress()).isSameAs(egress);
        assertThat(fresh.getEgress().isAllowPrivateAddresses()).isTrue();
    }
}
