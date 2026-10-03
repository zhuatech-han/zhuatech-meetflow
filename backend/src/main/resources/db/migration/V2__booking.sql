-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
CREATE TABLE meeting_resource (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL,
 code varchar(60) NOT NULL UNIQUE, name varchar(120) NOT NULL, location varchar(200) NOT NULL, category varchar(60) NOT NULL,
 department_id bigint NOT NULL, steward_id bigint NOT NULL, capacity int NOT NULL,
 open_minute int NOT NULL, close_minute int NOT NULL, buffer_minutes int NOT NULL, max_duration_minutes int NOT NULL,
 min_notice_minutes int NOT NULL, check_in_grace_minutes int NOT NULL, weekdays varchar(20) NOT NULL,
 approval_required boolean NOT NULL, shared boolean NOT NULL, enabled boolean NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id), FOREIGN KEY(steward_id) REFERENCES account(id),
 CHECK(capacity>=1 AND capacity<=10000), CHECK(open_minute>=0 AND close_minute<=1440 AND open_minute<close_minute),
 CHECK(buffer_minutes>=0 AND buffer_minutes<=120), CHECK(max_duration_minutes>=15 AND max_duration_minutes<=480),
 CHECK(min_notice_minutes>=0 AND min_notice_minutes<=1440), CHECK(check_in_grace_minutes>=5 AND check_in_grace_minutes<=60)
);
CREATE TABLE meeting_booking (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL, change_count bigint NOT NULL,
 resource_id bigint NOT NULL, owner_id bigint NOT NULL, department_id bigint NOT NULL, reviewer_id bigint NULL,
 title varchar(200) NOT NULL, purpose varchar(2000) NOT NULL, status varchar(20) NOT NULL,
 attendees int NOT NULL, starts_at timestamp(6) NOT NULL, ends_at timestamp(6) NOT NULL, occupancy_from timestamp(6) NOT NULL, occupied_until timestamp(6) NOT NULL,
 buffer_minutes int NOT NULL, check_in_grace_minutes int NOT NULL, checked_in_at timestamp(6) NULL, completed_at timestamp(6) NULL,
 submitted boolean NOT NULL, created_at timestamp(6) NOT NULL, updated_at timestamp(6) NOT NULL,
 FOREIGN KEY(resource_id) REFERENCES meeting_resource(id), FOREIGN KEY(owner_id) REFERENCES account(id),
 FOREIGN KEY(department_id) REFERENCES department(id), FOREIGN KEY(reviewer_id) REFERENCES account(id),
 CHECK(starts_at<ends_at AND attendees>0), CHECK(status IN ('DRAFT','PENDING','CONFIRMED','IN_USE','COMPLETED','REJECTED','CANCELLED','NO_SHOW','EXPIRED'))
);
CREATE TABLE resource_block (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL, resource_id bigint NOT NULL,
 reason varchar(1000) NOT NULL, status varchar(20) NOT NULL, starts_at timestamp(6) NOT NULL, ends_at timestamp(6) NOT NULL,
 actor varchar(60) NOT NULL, created_at timestamp(6) NOT NULL,
 FOREIGN KEY(resource_id) REFERENCES meeting_resource(id), CHECK(starts_at<ends_at), CHECK(status IN ('ACTIVE','CANCELLED'))
);
CREATE TABLE booking_event (
 id bigint AUTO_INCREMENT PRIMARY KEY, booking_id bigint NOT NULL, actor varchar(60) NOT NULL, action varchar(60) NOT NULL,
 note text NOT NULL, snapshot text NOT NULL, created_at timestamp(6) NOT NULL, FOREIGN KEY(booking_id) REFERENCES meeting_booking(id)
);
CREATE TABLE booking_command (
 id bigint AUTO_INCREMENT PRIMARY KEY, booking_id bigint NOT NULL, actor varchar(60) NOT NULL, request_key varchar(80) NOT NULL,
 fingerprint varchar(64) NOT NULL, UNIQUE(actor,request_key), FOREIGN KEY(booking_id) REFERENCES meeting_booking(id)
);
CREATE INDEX ix_booking_conflict ON meeting_booking(resource_id,status,starts_at,occupied_until);
CREATE INDEX ix_booking_owner ON meeting_booking(owner_id,status,starts_at);
CREATE INDEX ix_booking_scope ON meeting_booking(department_id,status,starts_at);
CREATE INDEX ix_block_conflict ON resource_block(resource_id,status,starts_at,ends_at);
CREATE INDEX ix_booking_event ON booking_event(booking_id,created_at);
