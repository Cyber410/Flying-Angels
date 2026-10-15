-- Original schema for fresh databases. Existing schemas are baselined at version 1.
CREATE TABLE athletes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, first_name VARCHAR(255), last_name VARCHAR(255), note VARCHAR(255),
 fly_status VARCHAR(255), flya_status VARCHAR(255), date_of_birth DATE, gender VARCHAR(255), import_key VARCHAR(64) UNIQUE
);
CREATE TABLE athlete_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, athlete_id BIGINT NOT NULL, event_name VARCHAR(255), result VARCHAR(255),
 CONSTRAINT fk_legacy_athlete FOREIGN KEY(athlete_id) REFERENCES athletes(id)
);
CREATE TABLE meets (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), meet_date DATE, location VARCHAR(255), external_url VARCHAR(255)
);
CREATE TABLE event_categories (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) UNIQUE, ranking_order ENUM('ASC','DESC')
);
CREATE TABLE events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), event_date DATE, location VARCHAR(255), event_type VARCHAR(255), external_url VARCHAR(255),
 meet_id BIGINT NOT NULL, category_id BIGINT NOT NULL, ranking_order ENUM('ASC','DESC'),
 CONSTRAINT fk_event_meet FOREIGN KEY(meet_id) REFERENCES meets(id),
 CONSTRAINT fk_event_category FOREIGN KEY(category_id) REFERENCES event_categories(id)
);
CREATE TABLE event_results (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, event_id BIGINT, category_id BIGINT NOT NULL, athlete_id BIGINT NOT NULL,
 result VARCHAR(50), score DECIMAL(12,4), UNIQUE KEY uk_legacy_result(event_id,athlete_id),
 CONSTRAINT fk_result_event FOREIGN KEY(event_id) REFERENCES events(id),
 CONSTRAINT fk_result_category FOREIGN KEY(category_id) REFERENCES event_categories(id),
 CONSTRAINT fk_result_athlete FOREIGN KEY(athlete_id) REFERENCES athletes(id)
);
