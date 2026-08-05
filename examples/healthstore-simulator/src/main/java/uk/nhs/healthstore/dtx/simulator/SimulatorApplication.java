package uk.nhs.healthstore.dtx.simulator;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Bean
    Clock clock(@Value("${simulator.fixed-time:}") String fixedTime) {
        if (fixedTime == null || fixedTime.isBlank()) {
            return Clock.systemUTC();
        }
        OffsetDateTime fixed = OffsetDateTime.parse(fixedTime);
        return Clock.fixed(fixed.toInstant(), fixed.getOffset());
    }
}
