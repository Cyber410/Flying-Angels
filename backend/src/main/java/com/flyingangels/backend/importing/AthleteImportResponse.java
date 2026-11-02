package com.flyingangels.backend.importing;
/** Compatibility count names plus explicit update/failure counts for immediate imports. */
public record AthleteImportResponse(int athleteCount, int eventCount, int duplicateCount,
                                   int updatedCount, int skippedCount, int failedCount) {}
