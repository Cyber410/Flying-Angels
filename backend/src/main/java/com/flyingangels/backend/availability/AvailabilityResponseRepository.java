package com.flyingangels.backend.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AvailabilityResponseRepository extends JpaRepository<AvailabilityResponse, Long> {
    List<AvailabilityResponse> findByRequestIdOrderByIdDesc(Long requestId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    java.util.Optional<AvailabilityResponse> findFirstByRequestIdOrderByIdDesc(Long requestId);
    @org.springframework.data.jpa.repository.Query("""
        select r from AvailabilityResponse r where r.request.id in :requestIds
        and not exists (select n.id from AvailabilityResponse n where n.request = r.request and n.id > r.id)
        """)
    List<AvailabilityResponse> latestForRequests(java.util.Collection<Long> requestIds);
    void deleteByRequestMeetId(Long meetId);
    void deleteByRequestAthleteId(Long athleteId);
}
