package uk.nhs.healthstore.dtx.simulator.store;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import uk.nhs.healthstore.dtx.simulator.api.model.LifecycleTask;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationServiceRequest;

@Component
public class RegistrationStore {

    public static final class Registration {
        private final RegistrationServiceRequest resource;
        private final String cohort;
        private int statusSeq;
        private final List<ReceivedTask> tasks = new ArrayList<>();

        private Registration(RegistrationServiceRequest resource, String cohort) {
            this.resource = resource;
            this.cohort = cohort;
        }

        public RegistrationServiceRequest resource() {
            return resource;
        }

        public String cohort() {
            return cohort;
        }

        public List<ReceivedTask> tasks() {
            return List.copyOf(tasks);
        }

        // A registration awaits action while no lifecycle Task has arrived
        // since its last status change; membership is derived, never stored.
        public boolean onWorklist() {
            return tasks.stream().noneMatch(t -> t.statusSeqAtArrival() == statusSeq);
        }
    }

    public record ReceivedTask(
            LifecycleTask task,
            int statusSeqAtArrival,
            UUID xRequestId,
            OffsetDateTime receivedAt) {
    }

    private final Map<UUID, Registration> registrations = new LinkedHashMap<>();
    private final AtomicInteger seedCounter = new AtomicInteger();

    public synchronized Registration seed(UUID id, RegistrationServiceRequest resource, String cohort) {
        Registration registration = new Registration(resource, cohort);
        registrations.put(id, registration);
        return registration;
    }

    public int nextSeed() {
        return seedCounter.incrementAndGet();
    }

    public synchronized Optional<Registration> find(UUID id) {
        return Optional.ofNullable(registrations.get(id));
    }

    public synchronized boolean cohortKnown(String cohort) {
        return registrations.values().stream().anyMatch(r -> cohort.equals(r.cohort));
    }

    public synchronized List<Registration> worklist(String cohort) {
        return registrations.values().stream()
                .filter(r -> cohort.equals(r.cohort))
                .filter(Registration::onWorklist)
                .toList();
    }

    public synchronized boolean recordTask(UUID id, LifecycleTask task, UUID xRequestId, OffsetDateTime now) {
        Registration registration = registrations.get(id);
        boolean repeat = registration.tasks.stream().anyMatch(t -> t.xRequestId().equals(xRequestId));
        if (repeat) {
            return false;
        }
        registration.tasks.add(new ReceivedTask(task, registration.statusSeq, xRequestId, now));
        return true;
    }

    public synchronized void setStatus(UUID id, RegistrationServiceRequest.StatusEnum status) {
        Registration registration = registrations.get(id);
        registration.resource.setStatus(status);
        registration.statusSeq++;
    }

    public synchronized void reset() {
        registrations.clear();
        seedCounter.set(0);
    }

    public synchronized Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> byId = new LinkedHashMap<>();
        var cohorts = new LinkedHashSet<String>();
        registrations.forEach((id, r) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", r.resource.getStatus().getValue());
            entry.put("cohort", r.cohort);
            entry.put("onWorklist", r.onWorklist());
            entry.put("receivedTasks", r.tasks.stream().map(t -> Map.of(
                    "status", t.task().getStatus().getValue(),
                    "businessStatus", String.valueOf(
                            t.task().getBusinessStatus().getText() != null
                                    ? t.task().getBusinessStatus().getText()
                                    : t.task().getBusinessStatus().getCoding()),
                    "xRequestId", t.xRequestId().toString(),
                    "receivedAt", t.receivedAt().toString())).toList());
            byId.put(id.toString(), entry);
            if (r.cohort != null) {
                cohorts.add(r.cohort);
            }
        });
        state.put("registrations", byId);
        state.put("cohorts", cohorts);
        return state;
    }
}
