package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.athlete.AgeRange;
import com.flyingangels.backend.event.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

public record ResultOverviewResponse(List<Row> content, int page, int size, long totalElements, int totalPages) {
    public static ResultOverviewResponse from(Page<EventResult> results) {
        return new ResultOverviewResponse(results.map(Row::from).getContent(),results.getNumber(),results.getSize(),
                results.getTotalElements(),results.getTotalPages());
    }
    public record Row(Long resultId,Long athleteId,String firstName,String lastName,String gender,LocalDate dateOfBirth,
                      Integer ageAtMeet,Long meetId,String meetName,LocalDate meetStartDate,Long eventId,String eventName,
                      LocalDate eventDate,Long categoryId,String categoryName,RankingOrder rankingOrder,
                      String result,BigDecimal score,ResultUnit unit,ResultStatus status,Instant recordedAt) {
        static Row from(EventResult result) {
            var athlete = result.getAthlete();
            var event = result.getEvent();
            var meet = event.getMeet();
            var start = meet.getStartDate() == null ? meet.getMeetDate() : meet.getStartDate();
            return new Row(result.getId(),athlete.getId(),athlete.getFirstName(),athlete.getLastName(),athlete.getGender(),
                    athlete.getDateOfBirth(),AgeRange.ageOn(athlete.getDateOfBirth(),start),meet.getId(),meet.getName(),start,
                    event.getId(),event.getName(),event.getEventDate(),result.getCategory().getId(),result.getCategory().getName(),
                    event.getRankingOrder(),result.getResult(),result.getScore(),result.getUnit(),result.getStatus(),result.getRecordedAt());
        }
    }
}
