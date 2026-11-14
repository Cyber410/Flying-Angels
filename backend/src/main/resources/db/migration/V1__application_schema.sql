-- Versioned MySQL 8.4 schema. Applied migrations must never be edited.

CREATE TABLE `athlete_import_jobs` (
  `id` varchar(36) NOT NULL,
  `content` longblob,
  `created_at` datetime(6) DEFAULT NULL,
  `filename` varchar(255) DEFAULT NULL,
  `source` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `athletes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `archived` tinyint(1) NOT NULL DEFAULT '0',
  `date_of_birth` date DEFAULT NULL,
  `external_id` varchar(150) DEFAULT NULL,
  `external_source` varchar(80) DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `fly_status` varchar(255) DEFAULT NULL,
  `flya_status` varchar(255) DEFAULT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `import_key` varchar(64) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_athlete` (`external_source`,`external_id`),
  UNIQUE KEY `UKc059uyvd67r3h4e0lxtclnu6m` (`import_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `event_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `ranking_order` enum('ASC','DESC') DEFAULT NULL,
  `unit` enum('METRES','POINTS','SECONDS') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1et3muobyw9w9dur2ww8bvhh7` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `meets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_date` date DEFAULT NULL,
  `external_url` varchar(500) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `meet_date` date DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `start_time` time DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `staff_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `athlete_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) NOT NULL,
  `invitation_pending` tinyint(1) NOT NULL DEFAULT '0',
  `name` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `password_set` tinyint(1) NOT NULL DEFAULT '1',
  `role` enum('ADMIN','ATHLETE','COACH') NOT NULL,
  `security_version` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcg5gp3sunabfoh3rwkqml3xmg` (`email`),
  UNIQUE KEY `UKpgy6fbj8i4fdikpvur1ibfvws` (`athlete_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_invitations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consumed_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `security_version` bigint NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2uscddiw4weydjhs13swcs6dk` (`token_hash`),
  KEY `idx_invitation_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `athlete_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_name` varchar(255) DEFAULT NULL,
  `result` varchar(255) DEFAULT NULL,
  `athlete_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm70py5jj5v83idi3poncy7t2q` (`athlete_id`),
  CONSTRAINT `FKm70py5jj5v83idi3poncy7t2q` FOREIGN KEY (`athlete_id`) REFERENCES `athletes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `athlete_import_rows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `athlete_id` bigint DEFAULT NULL,
  `explanation` varchar(2000) DEFAULT NULL,
  `repeated_of_row_number` int DEFAULT NULL,
  `file_row_number` int DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `job_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_row` (`job_id`,`file_row_number`),
  CONSTRAINT `FK173gl0ho7oiiiaodj2rko3p27` FOREIGN KEY (`job_id`) REFERENCES `athlete_import_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `availability_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answered_revision` bigint DEFAULT NULL,
  `assignment_count` int NOT NULL,
  `assignment_revision` bigint NOT NULL,
  `last_answer` enum('AVAILABLE','UNAVAILABLE') DEFAULT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `athlete_id` bigint NOT NULL,
  `meet_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_availability_meet_athlete` (`meet_id`,`athlete_id`),
  KEY `FKa5xp7p8j9ht4ldl6p0f0q31vo` (`athlete_id`),
  CONSTRAINT `FKa5xp7p8j9ht4ldl6p0f0q31vo` FOREIGN KEY (`athlete_id`) REFERENCES `athletes` (`id`),
  CONSTRAINT `FKkbcqsq73n4xsmm2k6kl3wbkq0` FOREIGN KEY (`meet_id`) REFERENCES `meets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_date` date DEFAULT NULL,
  `event_type` varchar(255) DEFAULT NULL,
  `external_url` varchar(500) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `note` varchar(2000) DEFAULT NULL,
  `ranking_order` enum('ASC','DESC') DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `meet_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meet_category` (`meet_id`,`category_id`),
  KEY `FKjgwxobdeneisw8h3nlaa2fwax` (`category_id`),
  CONSTRAINT `FKfy8p03crqqx6u5q9dojtlrl4m` FOREIGN KEY (`meet_id`) REFERENCES `meets` (`id`),
  CONSTRAINT `FKjgwxobdeneisw8h3nlaa2fwax` FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `athlete_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `text` varchar(2000) NOT NULL,
  `athlete_id` bigint NOT NULL,
  `event_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_note_order` (`athlete_id`,`created_at`,`id`),
  KEY `FKdpr3pm0ieum4qb2ohmgunl9ys` (`event_id`),
  CONSTRAINT `FKdpr3pm0ieum4qb2ohmgunl9ys` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `FKflkfkfelcheef5a6d53k07sws` FOREIGN KEY (`athlete_id`) REFERENCES `athletes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `availability_responses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answer` enum('AVAILABLE','UNAVAILABLE') NOT NULL,
  `assignment_revision` bigint NOT NULL,
  `athlete_user_id` bigint DEFAULT NULL,
  `communication_method` enum('EMAIL','IN_PERSON','OTHER','PHONE','PORTAL','TEXT','WHATSAPP') NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `responded_at` datetime(6) NOT NULL,
  `staff_user_id` bigint DEFAULT NULL,
  `submitted_version` bigint DEFAULT NULL,
  `request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_availability_response_request` (`request_id`,`id`),
  CONSTRAINT `FK58ke2abwt1u6fh2qghypi3840` FOREIGN KEY (`request_id`) REFERENCES `availability_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `event_participations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `note` varchar(2000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `athlete_id` bigint NOT NULL,
  `event_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_participation` (`event_id`,`athlete_id`),
  KEY `FK223b0big5jg1ai8raaaiet9bo` (`athlete_id`),
  CONSTRAINT `FK223b0big5jg1ai8raaaiet9bo` FOREIGN KEY (`athlete_id`) REFERENCES `athletes` (`id`),
  CONSTRAINT `FKop8sidel0e8jhh44ihbtulm17` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `event_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_id` varchar(100) DEFAULT NULL,
  `recorded_at` datetime(6) DEFAULT NULL,
  `result` varchar(50) DEFAULT NULL,
  `result_label` varchar(100) DEFAULT NULL,
  `score` decimal(12,4) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `status` enum('DNF','DNS','DQ','FOUL','NT','PENDING','UNPARSED','VALID') DEFAULT NULL,
  `unit` enum('METRES','POINTS','SECONDS') DEFAULT NULL,
  `athlete_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `event_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK33hl98186udquyc4r7p7e4lj6` (`client_id`),
  KEY `idx_result_ranking` (`category_id`,`athlete_id`,`status`,`unit`,`score`),
  KEY `FK16mwmta5os6h1y7009eduej6v` (`athlete_id`),
  KEY `FKrmqwb942jaoagmman4oulcqy9` (`event_id`),
  CONSTRAINT `FK16mwmta5os6h1y7009eduej6v` FOREIGN KEY (`athlete_id`) REFERENCES `athletes` (`id`),
  CONSTRAINT `FKmhe8osxtk98b2ay9oose9o73` FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`id`),
  CONSTRAINT `FKrmqwb942jaoagmman4oulcqy9` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invitation_email_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts` int NOT NULL,
  `claim_id` varchar(36) DEFAULT NULL,
  `claimed_until` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `encrypted_payload` varchar(4096) DEFAULT NULL,
  `invitation_id` bigint NOT NULL,
  `last_error_code` varchar(40) DEFAULT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','FAILED','PENDING','PROCESSING','SENT') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4bxfmjtycchlcpoxjr0r1hk7o` (`invitation_id`),
  KEY `idx_invitation_mail_due` (`status`,`next_attempt_at`,`claimed_until`),
  KEY `idx_invitation_mail_user` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `login_throttle_buckets` (
  `bucket_key` varchar(72) NOT NULL,
  `blocked_until` datetime(6) DEFAULT NULL,
  `failures` int NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `window_started_at` datetime(6) NOT NULL,
  PRIMARY KEY (`bucket_key`),
  KEY `idx_login_throttle_seen` (`last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
