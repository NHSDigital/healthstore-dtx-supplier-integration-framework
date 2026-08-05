package uk.nhs.healthstore.dtx.referencesupplier;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.ProcessAvailableServiceRequestsTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.ProcessSpecificServiceRequestTask;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.RegistrationRequestTask;

@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer registrationRequestTaskDeduction() {
        return builder -> builder.addMixIn(RegistrationRequestTask.class, RegistrationRequestTaskMixin.class);
    }

    // The generated oneOf interface carries no subtype information because the
    // contract has no discriminator property; deduction resolves the variant by
    // the presence of focus or groupIdentifier, which are mutually exclusive.
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
        @JsonSubTypes.Type(ProcessSpecificServiceRequestTask.class),
        @JsonSubTypes.Type(ProcessAvailableServiceRequestsTask.class)
    })
    interface RegistrationRequestTaskMixin {
    }
}
