package uk.nhs.healthstore.dtx.simulator.web;

public class NoAccessException extends RuntimeException {

    public NoAccessException() {
        super("No token, or a token that is invalid or expired");
    }
}
