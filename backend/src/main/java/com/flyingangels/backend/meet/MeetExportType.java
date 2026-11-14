package com.flyingangels.backend.meet;

public enum MeetExportType {
    REGISTRATION("Registration List", "registration"), RESULTS("Competition Results", "results");

    private final String displayName;
    private final String filenameSuffix;
    MeetExportType(String displayName, String filenameSuffix) {
        this.displayName = displayName; this.filenameSuffix = filenameSuffix;
    }
    public String displayName() { return displayName; }
    public String filenameSuffix() { return filenameSuffix; }
}
