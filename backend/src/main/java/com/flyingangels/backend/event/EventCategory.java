package com.flyingangels.backend.event;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_categories", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Enumerated(EnumType.STRING)
	private RankingOrder rankingOrder;

	public EventCategory(String name, RankingOrder rankingOrder) {
		this.name = name.trim();
		this.rankingOrder = rankingOrder;
	}

	public void update(String name, RankingOrder rankingOrder) {
		this.name = name.trim();
		this.rankingOrder = rankingOrder;
	}
}