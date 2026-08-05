package uk.nhs.healthstore.dtx.simulator.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// One credential set per supplier; the ODS code is the tenancy it resolves to.
@ConfigurationProperties(prefix = "simulator")
public record AuthSettings(List<Supplier> suppliers, int tokenTtlSeconds) {

    public record Supplier(String clientId, String clientSecret, String odsCode) {
    }
}
