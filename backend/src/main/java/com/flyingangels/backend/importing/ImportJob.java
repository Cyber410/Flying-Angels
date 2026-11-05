package com.flyingangels.backend.importing;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="athlete_import_jobs") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ImportJob {
    @Id @Column(length=36) private String id;
    private String filename;
    @Column(length=80) private String source;
    @Lob @Column(columnDefinition="LONGBLOB") private byte[] content;
    private Instant createdAt;
    public ImportJob(String filename,String source,byte[] content) { this.id=UUID.randomUUID().toString();this.filename=filename;this.source=source;this.content=content;this.createdAt=Instant.now(); }
}
