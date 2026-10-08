package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.SpecApi;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.service.SpecRepositoryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SpecController implements SpecApi {

    private final SpecRepositoryClient specRepositoryClient;

    public SpecController(SpecRepositoryClient specRepositoryClient) {
        this.specRepositoryClient = specRepositoryClient;
    }

    @Override
    public ResponseEntity<List<SpecFile>> listSpecs(String customerId) {
        return ResponseEntity.ok(specRepositoryClient.listSpecs(customerId));
    }
}
