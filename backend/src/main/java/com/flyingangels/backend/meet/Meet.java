package com.flyingangels.backend.meet;

import com.flyingangels.backend.event.Event;
import com.flyingangels.backend.meet.dto.MeetRequest;
import jakarta.persistence.CascadeType;
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

@Entity
@Table(name = "meets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;
	private LocalDate meetDate;
	private LocalDate endDate;
	private String location;
	private String externalUrl;

	@OneToMany(mappedBy = "meet", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Event> events = new ArrayList<>();

	public Meet(MeetRequest request) {
		updateFrom(request);
	}

	public void updateFrom(MeetRequest request) {
		if (request.meetDate() == null || (request.endDate() != null && request.endDate().isBefore(request.meetDate()))) {
			throw new IllegalArgumentException("End date must be on or after the start date");
		}
		LocalDate lastDate = request.endDate() == null ? request.meetDate() : request.endDate();
		if (events.stream().anyMatch(event -> event.getEventDate().isBefore(request.meetDate()) || event.getEventDate().isAfter(lastDate))) {
			throw new IllegalArgumentException("The meet date range must include all existing event dates");
		}
		this.name = request.name().trim();
		this.meetDate = request.meetDate();
		this.endDate = lastDate;
		this.location = request.location();
		this.externalUrl = request.externalUrl();
	}
}
