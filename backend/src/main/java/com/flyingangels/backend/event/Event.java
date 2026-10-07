package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.EventRequest;
import com.flyingangels.backend.meet.Meet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;
	private LocalDate eventDate;
	private String location;
	private String eventType;
	private String externalUrl;

	@ManyToOne(optional = false)
	@JoinColumn(name = "meet_id", nullable = false)
	private Meet meet;

	@ManyToOne(optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private EventCategory category;

	@Enumerated(EnumType.STRING)
	private RankingOrder rankingOrder;

	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<EventResult> results = new ArrayList<>();

	public Event(EventRequest request, EventCategory category, Meet meet) {
		updateFrom(request, category, meet);
	}

	public void updateFrom(EventRequest request, EventCategory category, Meet meet) {
		this.name = request.name().trim();
		this.eventDate = request.eventDate();
		this.location = request.location();
		this.eventType = request.eventType();
		this.externalUrl = request.externalUrl();
		this.rankingOrder = request.rankingOrder();
		this.category = category;
		this.meet = meet;
	}

	public void addResult(EventResult result) {
		results.add(result);
	}
}