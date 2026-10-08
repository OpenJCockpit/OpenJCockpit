package nl.metafactory.agents.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AgentRunCompositeIdTest {

    // --- AgentRunEventId ---

    @Test
    void eventId_isReflexive() {
        AgentRunEventId id = new AgentRunEventId("run-1", 1);
        assertTrue(id.equals(id));
    }

    @Test
    void eventId_equalInstancesHaveSameHashCode() {
        AgentRunEventId a = new AgentRunEventId("run-1", 1);
        AgentRunEventId b = new AgentRunEventId("run-1", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void eventId_differsByRunId() {
        AgentRunEventId a = new AgentRunEventId("run-1", 1);
        AgentRunEventId b = new AgentRunEventId("run-2", 1);
        assertNotEquals(a, b);
    }

    @Test
    void eventId_differsBySequenceNo() {
        AgentRunEventId a = new AgentRunEventId("run-1", 1);
        AgentRunEventId b = new AgentRunEventId("run-1", 2);
        assertNotEquals(a, b);
    }

    @Test
    void eventId_notEqualToNull() {
        AgentRunEventId id = new AgentRunEventId("run-1", 1);
        assertFalse(id.equals(null));
    }

    @Test
    void eventId_notEqualToUnrelatedType() {
        AgentRunEventId id = new AgentRunEventId("run-1", 1);
        assertFalse(id.equals("run-1"));
        AgentRunArtifactId artifact = new AgentRunArtifactId("run-1", 1);
        assertNotEquals(id, artifact);
    }

    @Test
    void eventId_settersAndGettersRoundTrip() {
        AgentRunEventId id = new AgentRunEventId();
        id.setRunId("run-9");
        id.setSequenceNo(9);
        assertEquals("run-9", id.getRunId());
        assertEquals(9, id.getSequenceNo());
    }

    // --- AgentRunArtifactId ---

    @Test
    void artifactId_isReflexive() {
        AgentRunArtifactId id = new AgentRunArtifactId("run-1", 1);
        assertTrue(id.equals(id));
    }

    @Test
    void artifactId_equalInstancesHaveSameHashCode() {
        AgentRunArtifactId a = new AgentRunArtifactId("run-1", 1);
        AgentRunArtifactId b = new AgentRunArtifactId("run-1", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void artifactId_differsByRunId() {
        AgentRunArtifactId a = new AgentRunArtifactId("run-1", 1);
        AgentRunArtifactId b = new AgentRunArtifactId("run-2", 1);
        assertNotEquals(a, b);
    }

    @Test
    void artifactId_differsBySequenceNo() {
        AgentRunArtifactId a = new AgentRunArtifactId("run-1", 1);
        AgentRunArtifactId b = new AgentRunArtifactId("run-1", 2);
        assertNotEquals(a, b);
    }

    @Test
    void artifactId_notEqualToNull() {
        AgentRunArtifactId id = new AgentRunArtifactId("run-1", 1);
        assertFalse(id.equals(null));
    }

    @Test
    void artifactId_notEqualToUnrelatedType() {
        AgentRunArtifactId id = new AgentRunArtifactId("run-1", 1);
        assertFalse(id.equals("run-1"));
        AgentRunEventId event = new AgentRunEventId("run-1", 1);
        assertNotEquals(id, event);
    }

    @Test
    void artifactId_settersAndGettersRoundTrip() {
        AgentRunArtifactId id = new AgentRunArtifactId();
        id.setRunId("run-10");
        id.setSequenceNo(10);
        assertEquals("run-10", id.getRunId());
        assertEquals(10, id.getSequenceNo());
    }
}
