package com.flyingangels.backend.meet.dto;

import com.flyingangels.backend.meet.MeetExportType;
import java.util.List;

public record MeetExportOptions(Long meetId, String meetName, List<Option> options) {
    public record Option(MeetExportType type, String displayName, long recordCount, boolean available) {
        public static Option from(MeetExportType type, long count) {
            return new Option(type, type.displayName(), count, count > 0);
        }
    }
}
