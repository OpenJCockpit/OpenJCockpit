package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.generated.api.SkillCatalogApi;
import nl.metafactory.aicontrol.model.SkillCatalogDto;
import nl.metafactory.aicontrol.service.SkillCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for the merged, read-only skill catalog. All aggregation, concurrency and
 * per-source status logic lives in {@link SkillCatalogService}. This endpoint always returns 200
 * — per-marketplace failures are data in {@code sources}, never an exception (BR-6) — so there is
 * no {@code @ExceptionHandler} here, unlike {@code SkillsMarketplaceController}.
 */
@RestController
public class SkillCatalogController implements SkillCatalogApi {

    private final SkillCatalogService service;

    public SkillCatalogController(SkillCatalogService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<SkillCatalogDto> getSkillCatalog() {
        return ResponseEntity.ok(service.getCatalog());
    }
}
