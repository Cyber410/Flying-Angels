package com.flyingangels.backend.athlete;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.flyingangels.backend.athlete.dto.AthleteRequest;

@Entity
@Table(name = "athletes", uniqueConstraints=@jakarta.persistence.UniqueConstraint(name="uk_external_athlete",columnNames={"external_source","external_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Athlete {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String firstName;
	private String lastName;
	@Column(length=500) private String note;
	private String flyStatus;
	private String flyaStatus;
	private LocalDate dateOfBirth;
	private String gender;

	@Column(unique = true, length = 64)
	private String importKey;
    @jakarta.persistence.Column(length=80) private String externalSource;
    @jakarta.persistence.Column(length=150) private String externalId;
    public void setExternalIdentity(String source,String id) { this.externalSource=source;this.externalId=id; }

	@OneToMany(mappedBy = "athlete", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AthleteEvent> events = new ArrayList<>();

	public Athlete(String firstName, String lastName, String note, String flyStatus, String flyaStatus,
			LocalDate dateOfBirth, String gender) {
		this(firstName, lastName, note, flyStatus, flyaStatus, dateOfBirth, gender, null);
	}

	public Athlete(String firstName, String lastName, String note, String flyStatus, String flyaStatus,
			LocalDate dateOfBirth, String gender, String importKey) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.note = note;
		this.flyStatus = normalizeStatus(flyStatus);
		this.flyaStatus = normalizeStatus(flyaStatus);
		this.dateOfBirth = dateOfBirth;
		this.gender = normalizeGender(gender);
		this.importKey = importKey;
	}

	public void addEvent(String eventName, String result) {
		events.add(new AthleteEvent(this, eventName, result));
	}

	public void updateFrom(AthleteRequest request) {
		this.firstName = request.firstName().trim();
		this.lastName = request.lastName().trim();
		if (request.note()!=null) this.note = request.note().trim();
		this.flyStatus = normalizeStatus(request.flyStatus());
		this.flyaStatus = normalizeStatus(request.flyaStatus());
		this.dateOfBirth = request.dateOfBirth();
		this.gender = normalizeGender(request.gender());
		if (request.events() != null) {
            this.events.clear();
			request.events().forEach(event -> addEvent(event.eventName().trim(), event.result().trim()));
		}
	}
    /** Import patches only supplied values. Empty cells never erase existing information. */
    public void mergeImported(AthleteRequest request, String source, String identifier) {
        if (hasValue(request.firstName())) firstName = request.firstName().trim();
        if (hasValue(request.lastName())) lastName = request.lastName().trim();
        if (hasValue(request.note())) note = request.note().trim();
        if (hasValue(request.flyStatus())) flyStatus = normalizeStatus(request.flyStatus());
        if (hasValue(request.flyaStatus())) flyaStatus = normalizeStatus(request.flyaStatus());
        if (request.dateOfBirth() != null) dateOfBirth = request.dateOfBirth();
        if (hasValue(request.gender())) gender = normalizeGender(request.gender());
        if (hasValue(identifier)) setExternalIdentity(source, identifier);
    }

    private static boolean hasValue(String value) { return value != null && !value.isBlank(); }

    public static String normalizeGender(String value) {
        if(value==null || value.isBlank()) return "";
        String clean=value.trim();
        return switch(clean.toLowerCase(java.util.Locale.ROOT)) {
            case "male", "men" -> "Male";
            case "female", "women" -> "Female";
            default -> clean;
        };
    }
    public static String normalizeStatus(String value) {
        if (value==null || value.isBlank()) return "";
        String clean=value.trim().replaceAll("\\s+"," ");
        return switch(clean.toLowerCase(java.util.Locale.ROOT)) {
            case "in roster" -> "In Roster";
            case "not on team" -> "Not On Team";
            case "not in roster" -> "Not In Roster";
            case "added" -> "Added";
            default -> clean;
        };
    }
}
