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
	private String location;
	private String externalUrl;

	@OneToMany(mappedBy = "meet", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Event> events = new ArrayList<>();

	public Meet(MeetRequest request) {
		updateFrom(request);
	}

	public void updateFrom(MeetRequest request) {
		this.name = request.name().trim();
		this.meetDate = request.meetDate();
		this.location = request.location();
		this.externalUrl = request.externalUrl();
	}
}