package nl.metafactory.aicontrol.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import nl.metafactory.aicontrol.model.ExecResult;
import nl.metafactory.aicontrol.service.ContainerRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Configuration
public class DockerContainerRuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(DockerContainerRuntimeConfig.class);

    @Bean
    @ConditionalOnProperty(name = "agentic.workflow.container.enabled", havingValue = "true", matchIfMissing = true)
    public ContainerRuntime dockerContainerRuntime(AgenticWorkflowProperties properties) {
        return new DockerContainerRuntime(buildDockerClient(properties.getContainer().getDockerHost()));
    }

    @Bean
    @ConditionalOnProperty(name = "agentic.workflow.container.enabled", havingValue = "false")
    public ContainerRuntime noOpContainerRuntime() {
        return new NoOpContainerRuntime();
    }

    private DockerClient buildDockerClient(String configuredDockerHost) {
        var configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (configuredDockerHost != null && !configuredDockerHost.isBlank()) {
            configBuilder.withDockerHost(configuredDockerHost.trim());
        }
        var config = configBuilder.build();
        URI dockerHost = config.getDockerHost() != null
                ? config.getDockerHost()
                : URI.create("unix:///var/run/docker.sock");
        log.info("Docker client connecting to daemon at {}", dockerHost);
        var http = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerHost)
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(10))
                .build();
        return DockerClientImpl.getInstance(config, http);
    }

    static class DockerContainerRuntime implements ContainerRuntime {

        private final DockerClient docker;

        DockerContainerRuntime(DockerClient docker) { this.docker = (DockerClient) docker; }

        @Override
        public void checkAvailability(String image, int timeoutSeconds) throws Exception {
            try {
                CompletableFuture.runAsync(() -> docker.pingCmd().exec())
                        .get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new Exception("Docker daemon ping timed out after " + timeoutSeconds + "s", e);
            } catch (Exception e) {
                throw new Exception("Docker daemon unreachable: " + rootMessage(e), e);
            }

            boolean imageExists;
            try {
                imageExists = !docker.listImagesCmd().withImageNameFilter(image).exec().isEmpty();
            } catch (Exception e) {
                throw new Exception("Unable to query local Docker images: " + rootMessage(e), e);
            }
            if (imageExists) return;

            try {
                docker.pullImageCmd(image)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new Exception("Configured image '" + image
                        + "' is not available locally and could not be pulled: " + rootMessage(e), e);
            }
        }

        private static String rootMessage(Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        }

        @Override
        public String createContainer(String image, UUID jobId, UUID projectId,
                                      long tmpfsSizeMb, long memoryBytes, Map<String, String> labels) {
            var hostConfig = HostConfig.newHostConfig()
                    .withTmpFs(Map.of("/workspace", "rw,size=" + tmpfsSizeMb + "m"))
                    .withMemory(memoryBytes)
                    .withCapDrop(Capability.ALL)
                    .withNetworkMode("bridge");

            var response = docker.createContainerCmd(image)
                    .withName("workflow-" + jobId)
                    .withLabels(labels)
                    .withHostConfig(hostConfig)
                    .withCmd("sleep", "infinity")
                    .withUser("1000:1000")
                    .exec();
            return response.getId();
        }

        @Override
        public void startContainer(String containerId) {
            docker.startContainerCmd(containerId).exec();
        }

        @Override
        public ExecResult executeInContainer(String containerId, List<String> command, Duration timeout) {
            try {
                ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
                        .withCmd(command.toArray(String[]::new))
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .exec();

                var stdout = new ByteArrayOutputStream();
                var stderr = new ByteArrayOutputStream();

                docker.execStartCmd(exec.getId())
                        .exec(new ResultCallback.Adapter<Frame>() {
                            @Override
                            public void onNext(Frame frame) {
                                try {
                                    switch (frame.getStreamType()) {
                                        case STDOUT -> stdout.write(frame.getPayload());
                                        case STDERR -> stderr.write(frame.getPayload());
                                        default -> { /* ignore */ }
                                    }
                                } catch (Exception ignored) {}
                            }
                        })
                        .awaitCompletion(timeout.getSeconds(), TimeUnit.SECONDS);

                var inspect = docker.inspectExecCmd(exec.getId()).exec();
                int code = inspect.getExitCodeLong() != null ? inspect.getExitCodeLong().intValue() : -1;
                return new ExecResult(code,
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.error("Docker exec failed in container {}", containerId, e);
                return new ExecResult(-1, "", e.getMessage());
            }
        }

        @Override
        public void copyIntoContainer(String containerId, byte[] content, String remotePath) {
            docker.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(new ByteArrayInputStream(content))
                    .withRemotePath(remotePath)
                    .exec();
        }

        @Override
        public void stopContainer(String containerId) {
            try { docker.stopContainerCmd(containerId).withTimeout(10).exec(); }
            catch (Exception e) { log.warn("Stop container {} failed: {}", containerId, e.getMessage()); }
        }

        @Override
        public void removeContainer(String containerId) {
            try { docker.removeContainerCmd(containerId).withForce(true).exec(); }
            catch (Exception e) { log.warn("Remove container {} failed: {}", containerId, e.getMessage()); }
        }

        @Override
        public List<String> findContainersByLabel(String labelKey, String labelValue) {
            var containers = docker.listContainersCmd()
                    .withLabelFilter(Map.of(labelKey, labelValue))
                    .withShowAll(true)
                    .exec();
            List<String> ids = new ArrayList<>();
            for (var c : containers) ids.add(c.getId());
            return ids;
        }
    }

    static class NoOpContainerRuntime implements ContainerRuntime {
        @Override public void checkAvailability(String image, int timeoutSeconds) {}
        @Override public String createContainer(String i, UUID j, UUID p, long t, long m, Map<String,String> l) { return "noop-" + j; }
        @Override public void startContainer(String id) {}
        @Override public ExecResult executeInContainer(String id, List<String> cmd, Duration t) { return new ExecResult(0, "", ""); }
        @Override public void copyIntoContainer(String id, byte[] c, String p) {}
        @Override public void stopContainer(String id) {}
        @Override public void removeContainer(String id) {}
        @Override public List<String> findContainersByLabel(String k, String v) { return List.of(); }
    }
}
