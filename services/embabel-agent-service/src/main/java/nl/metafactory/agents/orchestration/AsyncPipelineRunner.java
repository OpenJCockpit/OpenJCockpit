package nl.metafactory.agents.orchestration;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Separate bean so Spring's proxy can intercept @Async — self-invocation within
 * EmbabelOrchestrator would bypass the proxy and run synchronously.
 */
@Service
public class AsyncPipelineRunner {

    @Async
    public void run(Runnable task) {
        task.run();
    }
}
