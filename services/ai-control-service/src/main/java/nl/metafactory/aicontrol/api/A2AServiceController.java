package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.A2AServiceDto;
import nl.metafactory.aicontrol.client.A2AServiceRequest;
import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.generated.api.A2AServicesApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Relays the A2A services (Workflow designer → Configuration) to and from embabel-agent-service. */
@RestController
public class A2AServiceController implements A2AServicesApi {

    private final EmbabelAgentClient client;

    public A2AServiceController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<List<A2AServiceDto>> listA2AServices() {
        return ResponseEntity.ok(client.listA2AServices());
    }

    @Override
    public ResponseEntity<A2AServiceDto> createA2AService(A2AServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createA2AService(request));
    }

    @Override
    public ResponseEntity<A2AServiceDto> updateA2AService(String id, A2AServiceRequest request) {
        return ResponseEntity.ok(client.updateA2AService(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteA2AService(String id) {
        client.deleteA2AService(id);
        return ResponseEntity.noContent().build();
    }
}
