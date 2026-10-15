package com.flyingangels.backend.importing;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.event.EventService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ImportRowWriter {
    private final ImportRowRepository rows;
    private final AthleteImportMatcher matcher;
    private final AthleteRepository athletes;
    private final EventService events;
    private final EntityManager entityManager;

    static boolean eligible(String status) {
        // VALID supports previews created before create/update import classification was introduced.
        return status.equals("NEW") || status.equals("UPDATE") || status.equals("VALID");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commit(Long rowId, AthleteRegistrationParser.ParsedRow parsed) {
        var row = rows.lockById(rowId).orElseThrow();
        if (!eligible(row.getStatus())) return;
        var plan = matcher.plan(parsed, row.getJob().getSource());
        if (plan.athlete() != null) {
            // Serialize concurrent changes to a matched profile, then calculate its changes again.
            entityManager.refresh(plan.athlete(), LockModeType.PESSIMISTIC_WRITE);
            plan = matcher.plan(parsed, row.getJob().getSource());
        }
        if (!plan.action().equals("NEW") && !plan.action().equals("UPDATE")) {
            row.outcome(plan.action(), plan.explanation(), plan.athlete() == null ? null : plan.athlete().getId());
            return;
        }
        boolean created = plan.athlete() == null;
        var request = parsed.athlete();
        Athlete athlete = created ? new Athlete(request.firstName(), request.lastName(), request.note(),
                request.flyStatus(), request.flyaStatus(), request.dateOfBirth(), request.gender()) : plan.athlete();
        athlete.mergeImported(request, row.getJob().getSource(), parsed.externalId());
        // Append newly supplied historical values; earlier performances remain intact.
        plan.newEvents().forEach(e -> athlete.addEvent(e.eventName().trim(), e.result().trim()));
        athletes.saveAndFlush(athlete);
        events.recordImportedResults(athlete);
        row.outcome(created ? "IMPORTED" : "UPDATED", created ? "Created athlete" : "Updated: " + String.join(", ", plan.changedFields()), athlete.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long rowId) {
        var row = rows.lockById(rowId).orElseThrow();
        if (eligible(row.getStatus())) row.outcome("FAILED", "Record could not be saved; changes from this row were rolled back", null);
    }
}
