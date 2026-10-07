package com.flyingangels.backend.event;

import com.flyingangels.backend.athlete.Athlete;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "event_results", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "athlete_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "event_id", nullable = true)
	private Event event;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private EventCategory category;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "athlete_id", nullable = false)
	private Athlete athlete;

	@Column(length = 50)
	private String result;

	@Column(precision = 12, scale = 4)
	private BigDecimal score;

	public EventResult(Event event, EventCategory category, Athlete athlete, String result, BigDecimal score) {
		this.event = event;
		this.category = category;
		this.athlete = athlete;
		this.result = result.trim();
		this.score = score;
	}

	public void update(Athlete athlete, String result, BigDecimal score) {
		this.athlete = athlete;
		this.result = result.trim();
		this.score = score;
	}
}