package com.flyingangels.backend.athlete;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "athlete_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AthleteEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "athlete_id", nullable = false)
	private Athlete athlete;

	private String eventName;
	private String result;

	AthleteEvent(Athlete athlete, String eventName, String result) {
		this.athlete = athlete;
		this.eventName = eventName;
		this.result = result;
	}

}