package uk.nhs.healthstore.dtx.simulator.supplier;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uk.nhs.healthstore.dtx.simulator.supplier.model.AcceptedRegistrationRequestTask;
import uk.nhs.healthstore.dtx.simulator.supplier.model.AcceptedRegistrationRequestTaskFocus;
import uk.nhs.healthstore.dtx.simulator.supplier.model.CohortIdentifier;
import uk.nhs.healthstore.dtx.simulator.supplier.model.ProcessAvailableServiceRequestsTask;
import uk.nhs.healthstore.dtx.simulator.supplier.model.ProcessAvailableServiceRequestsTaskCode;
import uk.nhs.healthstore.dtx.simulator.supplier.model.ProcessAvailableServiceRequestsTaskCodeCodingInner;
import uk.nhs.healthstore.dtx.simulator.supplier.model.ProcessSpecificServiceRequestTask;
import uk.nhs.healthstore.dtx.simulator.supplier.model.ProcessSpecificServiceRequestTaskCode;
import uk.nhs.healthstore.dtx.simulator.supplier.model.ProcessSpecificServiceRequestTaskCodeCodingInner;
import uk.nhs.healthstore.dtx.simulator.supplier.model.RegistrationIdentifier;
import uk.nhs.healthstore.dtx.simulator.supplier.model.RegistrationRequestIdentifier;
import uk.nhs.healthstore.dtx.simulator.supplier.model.RequestPriority;

@Component
public class SupplierGateway {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    // A retry replays the request exactly: the Task and X-Request-ID are built
    // once per registration or cohort and reused on every send.
    private record Sent(Object task, UUID xRequestId) {
    }

    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, Sent> sent = new ConcurrentHashMap<>();

    public SupplierGateway(
            Clock clock,
            @Value("${simulator.supplier-base-url}") String supplierBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(supplierBaseUrl).build();
        this.clock = clock;
    }

    public Map<String, Object> sendSpecific(UUID registrationId, RequestPriority priority) {
        String key = "registration:" + registrationId;
        boolean redelivery = sent.containsKey(key);
        Sent request = sent.computeIfAbsent(key, k -> new Sent(specificTask(registrationId, priority), uuidFor(k)));
        return post(request, redelivery);
    }

    public Map<String, Object> sendAvailable(String cohort) {
        String key = "cohort:" + cohort;
        boolean redelivery = sent.containsKey(key);
        Sent request = sent.computeIfAbsent(key, k -> new Sent(availableTask(cohort), uuidFor(k)));
        return post(request, redelivery);
    }

    public void reset() {
        sent.clear();
    }

    private Map<String, Object> post(Sent request, boolean redelivery) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("redelivery", redelivery);
        result.put("xRequestId", request.xRequestId().toString());
        try {
            ResponseEntity<AcceptedRegistrationRequestTask> response = restClient.post()
                    .uri("/healthstore-registration-requests")
                    .contentType(FHIR_JSON)
                    .header("X-Request-ID", request.xRequestId().toString())
                    .body(request.task())
                    .retrieve()
                    .toEntity(AcceptedRegistrationRequestTask.class);
            result.put("status", response.getStatusCode().value());
            result.put("taskStatus", response.getBody() == null ? null : response.getBody().getStatus().getValue());
            result.put("lastModified", response.getBody() == null ? null : String.valueOf(response.getBody().getLastModified()));
        } catch (RestClientResponseException e) {
            result.put("status", e.getStatusCode().value());
            result.put("error", e.getResponseBodyAsString());
        }
        return result;
    }

    private ProcessSpecificServiceRequestTask specificTask(UUID registrationId, RequestPriority priority) {
        return new ProcessSpecificServiceRequestTask()
                .resourceType(ProcessSpecificServiceRequestTask.ResourceTypeEnum.TASK)
                .identifier(List.of(requestIdentifier("registration:" + registrationId)))
                .status(ProcessSpecificServiceRequestTask.StatusEnum.REQUESTED)
                .intent(ProcessSpecificServiceRequestTask.IntentEnum.ORDER)
                .code(new ProcessSpecificServiceRequestTaskCode().coding(List.of(
                        new ProcessSpecificServiceRequestTaskCodeCodingInner()
                                .system(ProcessSpecificServiceRequestTaskCodeCodingInner.SystemEnum
                                        .HTTPS_FHIR_HEALTHSTORE_NHS_UK_CODE_SYSTEM_TASK_CODE)
                                .code(ProcessSpecificServiceRequestTaskCodeCodingInner.CodeEnum
                                        .PROCESS_SPECIFIC_SERVICE_REQUEST)
                                .display(ProcessSpecificServiceRequestTaskCodeCodingInner.DisplayEnum
                                        .PROCESS_SPECIFIC_SERVICE_REQUEST))))
                .priority(priority)
                .authoredOn(OffsetDateTime.now(clock))
                .focus(new AcceptedRegistrationRequestTaskFocus()
                        .identifier(new RegistrationIdentifier()
                                .system(RegistrationIdentifier.SystemEnum.HTTPS_FHIR_HEALTHSTORE_NHS_UK_ID_REGISTRATION)
                                .value(registrationId)));
    }

    private ProcessAvailableServiceRequestsTask availableTask(String cohort) {
        return new ProcessAvailableServiceRequestsTask()
                .resourceType(ProcessAvailableServiceRequestsTask.ResourceTypeEnum.TASK)
                .identifier(List.of(requestIdentifier("cohort:" + cohort)))
                .status(ProcessAvailableServiceRequestsTask.StatusEnum.REQUESTED)
                .intent(ProcessAvailableServiceRequestsTask.IntentEnum.ORDER)
                .code(new ProcessAvailableServiceRequestsTaskCode().coding(List.of(
                        new ProcessAvailableServiceRequestsTaskCodeCodingInner()
                                .system(ProcessAvailableServiceRequestsTaskCodeCodingInner.SystemEnum
                                        .HTTPS_FHIR_HEALTHSTORE_NHS_UK_CODE_SYSTEM_TASK_CODE)
                                .code(ProcessAvailableServiceRequestsTaskCodeCodingInner.CodeEnum
                                        .PROCESS_AVAILABLE_SERVICE_REQUESTS)
                                .display(ProcessAvailableServiceRequestsTaskCodeCodingInner.DisplayEnum
                                        .PROCESS_AVAILABLE_SERVICE_REQUESTS))))
                .priority(RequestPriority.ROUTINE)
                .authoredOn(OffsetDateTime.now(clock))
                .groupIdentifier(new CohortIdentifier()
                        .system(CohortIdentifier.SystemEnum.HTTPS_FHIR_HEALTHSTORE_NHS_UK_ID_COHORT)
                        .value(cohort));
    }

    private RegistrationRequestIdentifier requestIdentifier(String key) {
        return new RegistrationRequestIdentifier()
                .system(RegistrationRequestIdentifier.SystemEnum.HTTPS_FHIR_HEALTHSTORE_NHS_UK_ID_REGISTRATION_REQUEST)
                .value(uuidFor("request:" + key));
    }

    private static UUID uuidFor(String key) {
        return UUID.nameUUIDFromBytes(("healthstore-simulator-" + key).getBytes(StandardCharsets.UTF_8));
    }
}
