package uk.nhs.healthstore.dtx.simulator.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import uk.nhs.healthstore.dtx.simulator.api.LifecycleApi;
import uk.nhs.healthstore.dtx.simulator.api.RegistrationsApi;
import uk.nhs.healthstore.dtx.simulator.api.model.LifecycleTask;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationSearchsetBundle;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationSearchsetBundleEntryInner;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationSearchsetBundleLinkInner;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationServiceRequest;
import uk.nhs.healthstore.dtx.simulator.store.RegistrationStore;

@RestController
public class RegistrationsController implements RegistrationsApi, LifecycleApi {

    private final RegistrationStore store;
    private final Clock clock;
    private final String publicBaseUrl;

    public RegistrationsController(
            RegistrationStore store,
            Clock clock,
            @Value("${simulator.public-base-url}") String publicBaseUrl) {
        this.store = store;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<RegistrationServiceRequest> getRegistration(
            UUID registrationId, UUID xRequestID, String xCorrelationID) {
        RegistrationStore.Registration registration = store.find(registrationId)
                .orElseThrow(() -> new NotKnownException("The registration is not known"));
        return ResponseEntity.ok(registration.resource());
    }

    @Override
    public ResponseEntity<RegistrationSearchsetBundle> searchRegistrations(
            String cohort, Integer count, Integer page, UUID xRequestID, String xCorrelationID) {
        if (!store.cohortKnown(cohort)) {
            throw new NotKnownException("The cohort is not known");
        }
        List<RegistrationStore.Registration> worklist = store.worklist(cohort);
        int from = Math.min((page - 1) * count, worklist.size());
        int to = Math.min(from + count, worklist.size());

        List<RegistrationSearchsetBundleLinkInner> links = new ArrayList<>();
        links.add(link(RegistrationSearchsetBundleLinkInner.RelationEnum.SELF, cohort, count, page));
        if (page > 1) {
            links.add(link(RegistrationSearchsetBundleLinkInner.RelationEnum.PREVIOUS, cohort, count, page - 1));
        }
        if (to < worklist.size()) {
            links.add(link(RegistrationSearchsetBundleLinkInner.RelationEnum.NEXT, cohort, count, page + 1));
        }

        RegistrationSearchsetBundle bundle = new RegistrationSearchsetBundle(
                RegistrationSearchsetBundle.ResourceTypeEnum.BUNDLE,
                RegistrationSearchsetBundle.TypeEnum.SEARCHSET,
                links)
                .entry(worklist.subList(from, to).stream()
                        .map(r -> new RegistrationSearchsetBundleEntryInner(r.resource()))
                        .toList());
        return ResponseEntity.ok(bundle);
    }

    @Override
    public ResponseEntity<Void> postRegistrationTask(
            UUID registrationId, UUID xRequestID, LifecycleTask lifecycleTask, String xCorrelationID) {
        store.find(registrationId)
                .orElseThrow(() -> new NotKnownException("The registration is not known"));
        store.recordTask(registrationId, lifecycleTask, xRequestID, OffsetDateTime.now(clock));
        return ResponseEntity.ok().build();
    }

    private RegistrationSearchsetBundleLinkInner link(
            RegistrationSearchsetBundleLinkInner.RelationEnum relation, String cohort, int count, int page) {
        String url = "%s/registrations?cohort=%s&_count=%d&page=%d".formatted(
                publicBaseUrl, URLEncoder.encode(cohort, StandardCharsets.UTF_8), count, page);
        return new RegistrationSearchsetBundleLinkInner(relation, url);
    }
}
