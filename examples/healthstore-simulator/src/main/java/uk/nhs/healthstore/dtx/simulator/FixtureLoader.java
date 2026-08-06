package uk.nhs.healthstore.dtx.simulator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import uk.nhs.healthstore.dtx.simulator.api.model.ContainedPatientTelecomInner;
import uk.nhs.healthstore.dtx.simulator.api.model.EmailContactPoint;
import uk.nhs.healthstore.dtx.simulator.api.model.OdsOrganizationIdentifier;
import uk.nhs.healthstore.dtx.simulator.api.model.OdsReference;
import uk.nhs.healthstore.dtx.simulator.api.model.PhoneContactPoint;
import uk.nhs.healthstore.dtx.simulator.api.model.RegistrationServiceRequest;
import uk.nhs.healthstore.dtx.simulator.api.model.RequestPriority;

// Fixtures are complete registration payloads in the contract's wire format,
// one JSON file per patient under src/main/resources/fixtures. Seeding clones
// a fixture and assigns the registration identifier.
@Component
public class FixtureLoader {

    private final ObjectMapper mapper = wireMapper();
    private final Map<String, JsonNode> fixtures = new TreeMap<>();

    public FixtureLoader() throws IOException {
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath:fixtures/*.json")) {
            String name = resource.getFilename().replaceFirst("\\.json$", "");
            try (InputStream in = resource.getInputStream()) {
                fixtures.put(name, mapper.readTree(in));
            }
        }
        if (fixtures.isEmpty()) {
            throw new IllegalStateException("No fixtures found on classpath:fixtures/*.json");
        }
    }

    // The generated telecom oneOf is a bare marker interface, so the variant
    // is chosen here by the system value. A schema discriminator would let
    // the generator do this, but it mishandles enum discriminator properties.
    public static ObjectMapper wireMapper() {
        SimpleModule telecom = new SimpleModule().addDeserializer(
                ContainedPatientTelecomInner.class,
                new JsonDeserializer<>() {
                    @Override
                    public ContainedPatientTelecomInner deserialize(JsonParser p, DeserializationContext ctxt)
                            throws IOException {
                        ObjectMapper codec = (ObjectMapper) p.getCodec();
                        JsonNode node = codec.readTree(p);
                        String system = node.path("system").asText();
                        return switch (system) {
                            case "email" -> codec.treeToValue(node, EmailContactPoint.class);
                            case "phone" -> codec.treeToValue(node, PhoneContactPoint.class);
                            default -> throw new IllegalArgumentException(
                                    "telecom.system must be email or phone, was " + system);
                        };
                    }
                });
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(telecom);
    }

    public List<String> names() {
        return List.copyOf(fixtures.keySet());
    }

    public String nameForSeed(int seed) {
        List<String> names = names();
        return names.get((seed - 1) % names.size());
    }

    public RegistrationServiceRequest instantiate(
            String fixture, UUID registrationId, String priorityOverride, String performerOdsOverride,
            OffsetDateTime authoredOn) {
        JsonNode node = fixtures.get(fixture);
        if (node == null) {
            throw new IllegalArgumentException(
                    "Unknown fixture " + fixture + ", available: " + names());
        }
        try {
            RegistrationServiceRequest resource = mapper.treeToValue(node, RegistrationServiceRequest.class);
            resource.getIdentifier().getFirst().setValue(registrationId);
            resource.setAuthoredOn(authoredOn);
            if (priorityOverride != null) {
                resource.setPriority(RequestPriority.fromValue(priorityOverride));
            }
            if (performerOdsOverride != null) {
                resource.setPerformer(List.of(new OdsReference(new OdsOrganizationIdentifier(
                        OdsOrganizationIdentifier.SystemEnum.HTTPS_FHIR_NHS_UK_ID_ODS_ORGANIZATION_CODE,
                        performerOdsOverride))));
            }
            return resource;
        } catch (IOException e) {
            throw new UncheckedIOException("Fixture " + fixture + " does not parse as a registration", e);
        }
    }
}
