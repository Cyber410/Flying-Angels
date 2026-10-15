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
@Table(name = "athletes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Athlete {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String firstName;
	private String lastName;
	private String note;
	private String flyStatus;
	private String flyaStatus;
	private LocalDate dateOfBirth;
	private String gender;

	@Column(unique = true, length = 64)
	private String importKey;

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
		this.flyStatus = flyStatus;
		this.flyaStatus = flyaStatus;
		this.dateOfBirth = dateOfBirth;
		this.gender = gender;
		this.importKey = importKey;
	}

	public void addEvent(String eventName, String result) {
		events.add(new AthleteEvent(this, eventName, result));
	}

	public void updateFrom(AthleteRequest request) {
		this.firstName = request.firstName().trim();
		this.lastName = request.lastName().trim();
		this.note = request.note();
		this.flyStatus = request.flyStatus();
		this.flyaStatus = request.flyaStatus();
		this.dateOfBirth = request.dateOfBirth();
		this.gender = request.gender();
		this.events.clear();
		if (request.events() != null) {
			request.events().forEach(event -> addEvent(event.eventName().trim(), event.result().trim()));
		}
	}
}