package uk.nhs.healthstore.dtx.referencesupplier;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcome;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcomeIssueInner;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcomeIssueInnerDetails;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcomeIssueInnerDetailsCodingInner;

// The token endpoint answers with OAuth error JSON from its own handlers;
// everything else answers with the contract's OperationOutcome.
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OperationOutcome> unreadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return outcome(request,
                OperationOutcomeIssueInner.CodeEnum.STRUCTURE,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_FHIR_STRUCTURE,
                "Body is not valid FHIR", null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<OperationOutcome> missingHeader(
            MissingRequestHeaderException e, HttpServletRequest request) {
        return outcome(request,
                OperationOutcomeIssueInner.CodeEnum.REQUIRED,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.MISSING_HEADER,
                e.getHeaderName() + " is required", e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<OperationOutcome> typeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return outcome(request,
                OperationOutcomeIssueInner.CodeEnum.VALUE,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_VALUE,
                e.getName() + " is not valid", e.getName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OperationOutcome> invalidBody(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<OperationOutcomeIssueInner> issues = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> issue(
                        OperationOutcomeIssueInner.CodeEnum.VALUE,
                        OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_VALUE,
                        fieldError.getDefaultMessage(),
                        fieldError.getField()))
                .toList();
        return respond(request, issues);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<OperationOutcome> invalidParameter(
            HandlerMethodValidationException e, HttpServletRequest request) {
        return outcome(request,
                OperationOutcomeIssueInner.CodeEnum.VALUE,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_VALUE,
                e.getReason(), null);
    }

    private ResponseEntity<OperationOutcome> outcome(
            HttpServletRequest request,
            OperationOutcomeIssueInner.CodeEnum issueCode,
            OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum errorCode,
            String diagnostics,
            String expression) {
        return respond(request, List.of(issue(issueCode, errorCode, diagnostics, expression)));
    }

    private ResponseEntity<OperationOutcome> respond(
            HttpServletRequest request, List<OperationOutcomeIssueInner> issues) {
        OperationOutcome outcome = new OperationOutcome(
                OperationOutcome.ResourceTypeEnum.OPERATION_OUTCOME, issues);
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(FHIR_JSON);
        mirror(request, response, "X-Request-ID");
        mirror(request, response, "X-Correlation-ID");
        return response.body(outcome);
    }

    private OperationOutcomeIssueInner issue(
            OperationOutcomeIssueInner.CodeEnum issueCode,
            OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum errorCode,
            String diagnostics,
            String expression) {
        OperationOutcomeIssueInner issue = new OperationOutcomeIssueInner(
                OperationOutcomeIssueInner.SeverityEnum.ERROR, issueCode)
                .details(new OperationOutcomeIssueInnerDetails()
                        .coding(List.of(new OperationOutcomeIssueInnerDetailsCodingInner(errorCode))))
                .diagnostics(diagnostics);
        if (expression != null) {
            issue.expression(List.of(expression));
        }
        return issue;
    }

    private static void mirror(
            HttpServletRequest request, ResponseEntity.BodyBuilder response, String header) {
        String value = request.getHeader(header);
        if (value != null) {
            response.header(header, value);
        }
    }
}
