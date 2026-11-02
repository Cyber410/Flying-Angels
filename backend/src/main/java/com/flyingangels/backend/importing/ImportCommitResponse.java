package com.flyingangels.backend.importing;
import java.util.List;
public record ImportCommitResponse(String importId, int imported, int updated, int skipped, int failed,
                                   List<ImportPreviewResponse.Row> rows) {}
