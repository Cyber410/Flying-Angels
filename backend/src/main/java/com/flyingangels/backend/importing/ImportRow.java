package com.flyingangels.backend.importing;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="athlete_import_rows",uniqueConstraints=@UniqueConstraint(name="uk_import_row",columnNames={"job_id","file_row_number"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ImportRow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="job_id",nullable=false) private ImportJob job;
    @Column(name="file_row_number") private int rowNumber;
    @Column(nullable=false,length=20) private String status;
    @Column(length=2000) private String explanation;
    private Long athleteId;
    public ImportRow(ImportJob job,int rowNumber,String status,String explanation) { this.job=job;this.rowNumber=rowNumber;this.status=status;this.explanation=explanation==null?null:explanation.substring(0,Math.min(2000,explanation.length())); }
    public void outcome(String status,String explanation,Long athleteId) { this.status=status;this.explanation=explanation==null?null:explanation.substring(0,Math.min(2000,explanation.length()));this.athleteId=athleteId; }
}
