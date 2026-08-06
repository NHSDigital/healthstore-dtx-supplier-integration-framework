package uk.nhs.healthstore.dtx.simulator.control;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.nhs.healthstore.dtx.simulator.FixtureLoader;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationServiceRequest;
import uk.nhs.healthstore.dtx.simulator.api.model.RequestPriority;
import uk.nhs.healthstore.dtx.simulator.store.RegistrationStore;
import uk.nhs.healthstore.dtx.simulator.supplier.SupplierGateway;
import uk.nhs.healthstore.dtx.simulator.web.TokenIssuer;

@RestController
@RequestMapping("/_simulator")
public class ControlController {

    private final RegistrationStore store;
    private final SupplierGateway gateway;
    private final FixtureLoader fixtures;
    private final TokenIssuer issuer;
    private final Clock clock;

    public ControlController(
            RegistrationStore store,
            SupplierGateway gateway,
            FixtureLoader fixtures,
            TokenIssuer issuer,
            Clock clock) {
        this.store = store;
        this.gateway = gateway;
        this.fixtures = fixtures;
        this.issuer = issuer;
        this.clock = clock;
    }

    @GetMapping("/fixtures")
    public Map<String, Object> fixtures() {
        return Map.of("fixtures", fixtures.names());
    }

    @PostMapping("/registrations")
    public Map<String, Object> seedRegistration(
            @RequestParam(required = false) String fixture,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String cohort,
            @RequestParam(required = false) String performer) {
        Seeded seeded = seed(fixture, priority, cohort, performer);
        return Map.of("registrationId", seeded.id().toString(), "fixture", seeded.fixture());
    }

    @PostMapping("/cohorts/{cohort}/registrations")
    public Map<String, Object> seedCohort(
            @PathVariable String cohort,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) String performer) {
        List<Map<String, String>> seeded = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Seeded s = seed(null, null, cohort, performer);
            seeded.add(Map.of("registrationId", s.id().toString(), "fixture", s.fixture()));
        }
        return Map.of("cohort", cohort, "registrations", seeded);
    }

    @PostMapping("/registrations/{id}/send")
    public Map<String, Object> sendRegistrationRequest(@PathVariable UUID id) {
        RegistrationStore.Registration registration = store.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        RequestPriority priority = registration.resource().getPriority() == null
                ? RequestPriority.ASAP
                : registration.resource().getPriority();
        return gateway.sendSpecific(id, toClientPriority(priority));
    }

    @PostMapping("/cohorts/{cohort}/send")
    public Map<String, Object> sendCohortRequest(@PathVariable String cohort) {
        if (!store.cohortKnown(cohort)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return gateway.sendAvailable(cohort);
    }

    @PostMapping("/registrations/{id}/status/{status}")
    public Map<String, Object> setStatus(@PathVariable UUID id, @PathVariable String status) {
        if (!store.setStatus(id, RegistrationServiceRequest.StatusEnum.fromValue(status))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return Map.of("registrationId", id.toString(), "status", status);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return store.state();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.reset();
        gateway.reset();
        issuer.reset();
        return Map.of("reset", true);
    }

    private record Seeded(UUID id, String fixture) {
    }

    private Seeded seed(String fixture, String priority, String cohort, String performer) {
        int seq = store.nextSeed();
        String chosen = fixture != null ? fixture : fixtures.nameForSeed(seq);
        UUID id = registrationId(seq);
        try {
            RegistrationServiceRequest resource =
                    fixtures.instantiate(chosen, id, priority, performer, OffsetDateTime.now(clock));
            store.seed(id, resource, cohort);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return new Seeded(id, chosen);
    }

    private static UUID registrationId(int seq) {
        return UUID.nameUUIDFromBytes(
                ("healthstore-simulator-registration-" + seq).getBytes(StandardCharsets.UTF_8));
    }

    private static uk.nhs.healthstore.dtx.simulator.supplier.model.RequestPriority toClientPriority(
            RequestPriority priority) {
        return uk.nhs.healthstore.dtx.simulator.supplier.model.RequestPriority.fromValue(priority.getValue());
    }
}
