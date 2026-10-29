package com.flyingangels.backend.note;
import com.flyingangels.backend.athlete.AthleteService;
import com.flyingangels.backend.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor
public class AthleteNoteService {
    private final AthleteNoteRepository repository;
    private final AthleteService athletes;
    private final EventService events;
    @Transactional
    public AthleteNoteResponse create(Long id,AthleteNoteRequest request) {
        var athlete=athletes.get(id); var event=request.eventId()==null?null:events.get(request.eventId());
        return AthleteNoteResponse.from(repository.save(new AthleteNote(athlete,event,request.text())));
    }
    @Transactional(readOnly=true)
    public List<AthleteNoteResponse> list(Long id) { athletes.get(id);return repository.findByAthleteIdOrderByCreatedAtDescIdDesc(id).stream().map(AthleteNoteResponse::from).toList(); }
}
