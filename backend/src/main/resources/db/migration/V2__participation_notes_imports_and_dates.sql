-- Validate event duplicates before adding the required meet/category constraint.
-- If duplicates exist, resolve them deliberately before applying this migration.
ALTER TABLE meets ADD COLUMN start_date DATE, ADD COLUMN end_date DATE;
UPDATE meets SET start_date=meet_date,end_date=meet_date;
ALTER TABLE athletes MODIFY COLUMN note VARCHAR(500),
 ADD COLUMN external_source VARCHAR(80), ADD COLUMN external_id VARCHAR(150),
 ADD CONSTRAINT uk_external_athlete UNIQUE(external_source,external_id);
ALTER TABLE meets MODIFY COLUMN external_url VARCHAR(500);
ALTER TABLE events MODIFY COLUMN external_url VARCHAR(500),
 ADD CONSTRAINT uk_meet_category UNIQUE(meet_id,category_id);
CREATE TABLE event_participations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,event_id BIGINT NOT NULL,athlete_id BIGINT NOT NULL,
 CONSTRAINT uk_participation UNIQUE(event_id,athlete_id),
 CONSTRAINT fk_part_event FOREIGN KEY(event_id) REFERENCES events(id),
 CONSTRAINT fk_part_athlete FOREIGN KEY(athlete_id) REFERENCES athletes(id)
);
INSERT INTO event_participations(event_id,athlete_id)
 SELECT DISTINCT event_id,athlete_id FROM event_results WHERE event_id IS NOT NULL;
-- Hibernate-generated unique index names vary; find the legacy pair constraint by its columns.
DELIMITER //
CREATE PROCEDURE drop_legacy_result_unique()
BEGIN
 DECLARE index_name_to_drop VARCHAR(255);
 SELECT index_name INTO index_name_to_drop FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='event_results' AND non_unique=0 AND index_name<>'PRIMARY'
 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='event_id,athlete_id' LIMIT 1;
 IF index_name_to_drop IS NOT NULL THEN
  -- Preserve the index used by the FK before dropping its unique counterpart.
  ALTER TABLE event_results ADD INDEX idx_result_event(event_id);
  SET @drop_sql=CONCAT('ALTER TABLE event_results DROP INDEX `',REPLACE(index_name_to_drop,'`','``'),'`');
  PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;
 END IF;
END//
CALL drop_legacy_result_unique()//
DROP PROCEDURE drop_legacy_result_unique//
DELIMITER ;
ALTER TABLE event_categories ADD COLUMN unit ENUM('SECONDS','METRES','POINTS');
UPDATE event_categories SET unit=CASE WHEN LOWER(name) REGEXP 'point|athlon' THEN 'POINTS'
 WHEN LOWER(name) REGEXP 'jump|put|discus|javelin|hammer' OR ranking_order='DESC' THEN 'METRES' ELSE 'SECONDS' END;
ALTER TABLE event_results
 ADD COLUMN unit ENUM('SECONDS','METRES','POINTS'),
 ADD COLUMN status ENUM('VALID','NT','DNS','DNF','DQ','FOUL','PENDING','UNPARSED'),
 ADD COLUMN recorded_at DATETIME(6), ADD COLUMN source VARCHAR(255);
UPDATE event_results r JOIN event_categories c ON c.id=r.category_id
 SET r.unit=CASE WHEN LOWER(c.name) REGEXP 'point|athlon' THEN 'POINTS'
                WHEN LOWER(c.name) REGEXP 'jump|put|discus|javelin|hammer' OR c.ranking_order='DESC' THEN 'METRES' ELSE 'SECONDS' END,
 r.status=CASE WHEN r.result IS NULL THEN 'PENDING' WHEN r.score IS NULL THEN 'UNPARSED' ELSE 'VALID' END,
 r.source=CASE WHEN r.event_id IS NULL THEN 'LEGACY_IMPORT' ELSE 'MANUAL' END;
-- Recording times for old rows are intentionally left unknown rather than invented.
DELETE FROM event_results WHERE event_id IS NOT NULL AND result IS NULL AND score IS NULL;
CREATE TABLE athlete_notes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,athlete_id BIGINT NOT NULL,event_id BIGINT,text VARCHAR(2000) NOT NULL,created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_note_athlete FOREIGN KEY(athlete_id) REFERENCES athletes(id),
 CONSTRAINT fk_note_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE SET NULL,
 INDEX idx_note_order(athlete_id,created_at,id)
);
CREATE TABLE athlete_import_jobs (
 id VARCHAR(36) NOT NULL PRIMARY KEY,filename VARCHAR(255),source VARCHAR(80),content LONGBLOB,created_at DATETIME(6)
);
CREATE TABLE athlete_import_rows (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,job_id VARCHAR(36) NOT NULL,file_row_number INT NOT NULL,status VARCHAR(20) NOT NULL,
 explanation VARCHAR(2000),athlete_id BIGINT,
 CONSTRAINT uk_import_row UNIQUE(job_id,file_row_number),
 CONSTRAINT fk_import_job FOREIGN KEY(job_id) REFERENCES athlete_import_jobs(id)
);
