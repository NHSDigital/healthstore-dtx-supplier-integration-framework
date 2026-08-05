package uk.nhs.healthstore.dtx.simulator.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import uk.nhs.healthstore.dtx.simulator.api.model.OperationOutcome;
import uk.nhs.healthstore.dtx.simulator.api.model.OperationOutcomeIssueInner;
import uk.nhs.healthstore.dtx.simulator.api.model.OperationOutcomeIssueInnerDetails;
import uk.nhs.healthstore.dtx.simulator.api.model.OperationOutcomeIssueInnerDetailsCodingInner;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

    @ExceptionHandler(NotKnownException.class)
    public ResponseEntity<OperationOutcome> notKnown(NotKnownException e) {
        return outcome(HttpStatus.NOT_FOUND,
                OperationOutcomeIssueInner.CodeEnum.NOT_FOUND,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.REFERENCE_NOT_FOUND,
                e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OperationOutcome> invalidBody(MethodArgumentNotValidException e) {
        List<OperationOutcomeIssueInner> issues = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> issue(
                        OperationOutcomeIssueInner.CodeEnum.VALUE,
                        OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_VALUE,
                        fieldError.getDefaultMessage(),
                        fieldError.getField()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, issues);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OperationOutcome> unreadable(HttpMessageNotReadableException e) {
        return outcome(HttpStatus.BAD_REQUEST,
                OperationOutcomeIssueInner.CodeEnum.STRUCTURE,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_FHIR_STRUCTURE,
                "Body is not valid FHIR", null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<OperationOutcome> missingHeader(MissingRequestHeaderException e) {
        return outcome(HttpStatus.BAD_REQUEST,
                OperationOutcomeIssueInner.CodeEnum.REQUIRED,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.MISSING_HEADER,
                e.getHeaderName() + " is required", e.getHeaderName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<OperationOutcome> missingParameter(MissingServletRequestParameterException e) {
        return outcome(HttpStatus.BAD_REQUEST,
                OperationOutcomeIssueInner.CodeEnum.REQUIRED,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.MISSING_PARAMETER,
                e.getParameterName() + " is required", e.getParameterName());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<OperationOutcome> invalidParameter(HandlerMethodValidationException e) {
        return outcome(HttpStatus.BAD_REQUEST,
                OperationOutcomeIssueInner.CodeEnum.VALUE,
                OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.INVALID_VALUE,
                e.getReason(), null);
    }

    private ResponseEntity<OperationOutcome> outcome(
            HttpStatus status,
            OperationOutcomeIssueInner.CodeEnum issueCode,
            OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum errorCode,
            String diagnostics,
            String expression) {
        return respond(status, List.of(issue(issueCode, errorCode, diagnostics, expression)));
    }

    private ResponseEntity<OperationOutcome> respond(HttpStatus status, List<OperationOutcomeIssueInner> issues) {
        OperationOutcome outcome = new OperationOutcome(
                OperationOutcome.ResourceTypeEnum.OPERATION_OUTCOME, issues);
        return ResponseEntity.status(status).contentType(FHIR_JSON).body(outcome);
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
}
