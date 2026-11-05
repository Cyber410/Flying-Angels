package com.flyingangels.backend.importing;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import java.util.List;
public record ImportPreviewResponse(String importId,List<Row> rows) {
    public record Row(int rowNumber,String status,String explanation,Long athleteId,AthleteRequest athlete,String externalId,
                      Integer repeatedOfRowNumber) {}
}
