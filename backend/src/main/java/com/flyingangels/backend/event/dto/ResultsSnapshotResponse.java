package com.flyingangels.backend.event.dto;

import java.util.List;

public record ResultsSnapshotResponse(List<EventResponse> events, List<ResultOverviewResponse.Row> results) {}
