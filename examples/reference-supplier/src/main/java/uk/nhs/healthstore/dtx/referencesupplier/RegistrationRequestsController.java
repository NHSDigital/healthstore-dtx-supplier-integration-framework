package uk.nhs.healthstore.dtx.referencesupplier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.healthstore.dtx.referencesupplier.api.RegistrationRequestsApi;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.AcceptedRegistrationRequestTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.ProcessAvailableServiceRequestsTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.ProcessSpecificServiceRequestTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.RegistrationRequestIdentifier;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.RegistrationRequestTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.RequestPriority;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.TaskCode;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.TaskCodeCodingInner;

@RestController
public class RegistrationRequestsController implements RegistrationRequestsApi {

    @Override
    public ResponseEntity<AcceptedRegistrationRequestTask> postRegistrationRequest(
            UUID xRequestID,
            RegistrationRequestTask registrationRequestTask,
            String xCorrelationID) {
        AcceptedRegistrationRequestTask accepted = accept(registrationRequestTask);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header("X-Request-ID", xRequestID.toString());
        if (xCorrelationID != null) {
            response.header("X-Correlation-ID", xCorrelationID);
        }
        return response.body(accepted);
    }

    private AcceptedRegistrationRequestTask accept(RegistrationRequestTask request) {
        return switch (request) {
            case ProcessSpecificServiceRequestTask specific -> {
                AcceptedRegistrationRequestTask task = acceptedTask(
                        specific.getIdentifier(),
                        TaskCodeCodingInner.CodeEnum.PROCESS_SPECIFIC_SERVICE_REQUEST,
                        TaskCodeCodingInner.DisplayEnum.PROCESS_SPECIFIC_SERVICE_REQUEST,
                        specific.getPriority(),
                        specific.getAuthoredOn());
                task.setFocus(specific.getFocus());
                yield task;
            }
            case ProcessAvailableServiceRequestsTask available -> {
                AcceptedRegistrationRequestTask task = acceptedTask(
                        available.getIdentifier(),
                        TaskCodeCodingInner.CodeEnum.PROCESS_AVAILABLE_SERVICE_REQUESTS,
                        TaskCodeCodingInner.DisplayEnum.PROCESS_AVAILABLE_SERVICE_REQUESTS,
                        available.getPriority(),
                        available.getAuthoredOn());
                task.setGroupIdentifier(available.getGroupIdentifier());
                yield task;
            }
            default -> throw new IllegalStateException("Unknown registration request variant");
        };
    }

    private AcceptedRegistrationRequestTask acceptedTask(
            List<RegistrationRequestIdentifier> identifier,
            TaskCodeCodingInner.CodeEnum code,
            TaskCodeCodingInner.DisplayEnum display,
            RequestPriority priority,
            OffsetDateTime authoredOn) {
        TaskCodeCodingInner coding = new TaskCodeCodingInner(
                TaskCodeCodingInner.SystemEnum.HTTPS_FHIR_HEALTHSTORE_NHS_UK_CODE_SYSTEM_TASK_CODE,
                code);
        coding.setDisplay(display);
        return new AcceptedRegistrationRequestTask(
                AcceptedRegistrationRequestTask.ResourceTypeEnum.TASK,
                identifier,
                AcceptedRegistrationRequestTask.StatusEnum.ACCEPTED,
                AcceptedRegistrationRequestTask.IntentEnum.ORDER,
                new TaskCode(List.of(coding)),
                priority,
                authoredOn,
                OffsetDateTime.now());
    }
}
