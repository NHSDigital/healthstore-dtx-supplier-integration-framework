package uk.nhs.healthstore.dtx.referencesupplier;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.ProcessAvailableServiceRequestsTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.ProcessSpecificServiceRequestTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.RegistrationRequestTask;

@Configuration
public class JacksonConfig {

    // Picks the Task variant by its code value, refusing any payload whose
    // focus or groupIdentifier disagrees with it.
    @Bean
    JsonMapperBuilderCustomizer registrationRequestTaskDeserialization() {
        SimpleModule module = new SimpleModule().addDeserializer(
                RegistrationRequestTask.class,
                new ValueDeserializer<RegistrationRequestTask>() {
                    @Override
                    public RegistrationRequestTask deserialize(JsonParser p, DeserializationContext ctxt) {
                        JsonNode node = ctxt.readTree(p);
                        String code = node.path("code").path("coding").path(0).path("code").asString("");
                        boolean hasFocus = node.has("focus");
                        boolean hasGroup = node.has("groupIdentifier");
                        return switch (code) {
                            case "process-specific-service-request" -> hasFocus && !hasGroup
                                    ? ctxt.readTreeAsValue(node, ProcessSpecificServiceRequestTask.class)
                                    : disagreement(ctxt);
                            case "process-available-service-requests" -> hasGroup && !hasFocus
                                    ? ctxt.readTreeAsValue(node, ProcessAvailableServiceRequestsTask.class)
                                    : disagreement(ctxt);
                            default -> ctxt.reportInputMismatch(RegistrationRequestTask.class,
                                    "code must name a task-code value, was '%s'", code);
                        };
                    }

                    private RegistrationRequestTask disagreement(DeserializationContext ctxt) {
                        return ctxt.reportInputMismatch(RegistrationRequestTask.class,
                                "code, focus and groupIdentifier disagree");
                    }
                });
        return builder -> builder.addModule(module);
    }
}
