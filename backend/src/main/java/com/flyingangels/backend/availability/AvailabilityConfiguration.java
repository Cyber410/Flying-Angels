package com.flyingangels.backend.availability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import java.time.*;

@Configuration
public class AvailabilityConfiguration {
    @Bean Clock availabilityClock(@Value("${app.availability.time-zone:America/Toronto}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
    @Bean ApplicationRunner initializeAvailability(AvailabilityService availability) {
        return args -> availability.initializeExistingAssignments();
    }
}
