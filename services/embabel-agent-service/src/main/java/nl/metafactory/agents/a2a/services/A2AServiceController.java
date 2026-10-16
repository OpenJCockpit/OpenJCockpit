package nl.metafactory.agents.a2a.services;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** A2A services for the dashboard's Workflow designer → Configuration tab (see {@link A2AServices}). */
@RestController
@RequestMapping("/api/a2a-services")
public class A2AServiceController {

    private final A2AServices services;

    public A2AServiceController(A2AServices services) {
        this.services = services;
    }

    @GetMapping
    public List<A2AServiceDto> list() {
        return services.list();
    }

    @PostMapping
    public ResponseEntity<A2AServiceDto> create(@RequestBody A2AServiceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(services.create(request, actor(jwt)));
    }

    @PutMapping("/{id}")
    public A2AServiceDto update(@PathVariable String id, @RequestBody A2AServiceRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return services.update(id, request, actor(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        actor(jwt);
        services.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Changes are attributed to the caller's subject; without one nothing is changed. */
    private static String actor(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A signed-in user is required");
        }
        return jwt.getSubject();
    }
}
