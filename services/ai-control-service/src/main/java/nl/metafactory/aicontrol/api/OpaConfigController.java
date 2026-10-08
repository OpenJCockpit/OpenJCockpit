package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.OpaConfigDto;
import nl.metafactory.aicontrol.client.OpaConfigRequestDto;
import nl.metafactory.aicontrol.generated.api.OpaConfigApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OpaConfigController implements OpaConfigApi {

    private final EmbabelAgentClient client;

    public OpaConfigController(EmbabelAgentClient client) {
        this.client = client;
    }

    @Override
    public ResponseEntity<OpaConfigDto> get() {
        return ResponseEntity.ok(client.getOpaConfig());
    }

    @Override
    public ResponseEntity<OpaConfigDto> update(OpaConfigRequestDto request) {
        return ResponseEntity.ok(client.saveOpaConfig(request));
    }

    @Override
    public ResponseEntity<Map<String, Boolean>> health() {
        return ResponseEntity.ok(Map.of("reachable", client.checkOpaHealth()));
    }
}
