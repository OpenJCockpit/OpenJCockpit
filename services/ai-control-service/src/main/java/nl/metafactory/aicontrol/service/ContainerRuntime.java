package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.ExecResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ContainerRuntime {

    void checkAvailability(String image, int timeoutSeconds) throws Exception;

    String createContainer(String image, UUID jobId, UUID projectId,
                           long tmpfsSizeMb, long memoryBytes, Map<String, String> labels);

    void startContainer(String containerId);

    ExecResult executeInContainer(String containerId, List<String> command, Duration timeout);

    void copyIntoContainer(String containerId, byte[] content, String remotePath);

    void stopContainer(String containerId);

    void removeContainer(String containerId);

    List<String> findContainersByLabel(String labelKey, String labelValue);
}
