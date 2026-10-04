-- Reference schema (MySQL 8).
-- You do NOT have to run this file: the backend creates/updates these tables automatically
-- (spring.jpa.hibernate.ddl-auto=update) the first time it starts. It is provided for documentation
-- and for anyone who prefers to create the schema manually.

USE gym_db;

CREATE TABLE IF NOT EXISTS membership_plans (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(60)   NOT NULL UNIQUE,
    duration_months INT           NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    description     VARCHAR(255),
    active          BIT           NOT NULL
);

CREATE TABLE IF NOT EXISTS trainers (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    email            VARCHAR(120) NOT NULL UNIQUE,
    phone            VARCHAR(20)  NOT NULL,
    specialization   VARCHAR(100),
    experience_years INT          NOT NULL,
    availability     VARCHAR(100),
    active           BIT          NOT NULL
);

CREATE TABLE IF NOT EXISTS members (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_code           VARCHAR(20)  UNIQUE,
    full_name             VARCHAR(100) NOT NULL,
    email                 VARCHAR(120) NOT NULL UNIQUE,
    phone                 VARCHAR(20)  NOT NULL UNIQUE,
    date_of_birth         DATE         NOT NULL,
    gender                VARCHAR(10)  NOT NULL,
    address               VARCHAR(255),
    emergency_contact     VARCHAR(100) NOT NULL,
    join_date             DATE         NOT NULL,
    plan_id               BIGINT       NOT NULL,
    membership_start_date DATE         NOT NULL,
    membership_end_date   DATE         NOT NULL,
    trainer_id            BIGINT       NULL,
    payment_status        VARCHAR(10)  NOT NULL,
    CONSTRAINT fk_members_plan    FOREIGN KEY (plan_id)    REFERENCES membership_plans (id),
    CONSTRAINT fk_members_trainer FOREIGN KEY (trainer_id) REFERENCES trainers (id)
);

CREATE TABLE IF NOT EXISTS payments (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id             BIGINT        NOT NULL,
    plan_id               BIGINT        NOT NULL,
    amount                DECIMAL(10,2) NOT NULL,
    discount_percent      DECIMAL(5,2)  NOT NULL,
    final_amount          DECIMAL(10,2) NOT NULL,
    method                VARCHAR(10)   NOT NULL,
    status                VARCHAR(10)   NOT NULL,
    payment_date          DATE          NOT NULL,
    transaction_reference VARCHAR(40)   NOT NULL UNIQUE,
    CONSTRAINT fk_payments_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_payments_plan   FOREIGN KEY (plan_id)   REFERENCES membership_plans (id)
);

CREATE TABLE IF NOT EXISTS attendance (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT NOT NULL,
    attendance_date DATE   NOT NULL,
    check_in_time   TIME   NOT NULL,
    CONSTRAINT uk_attendance_member_date UNIQUE (member_id, attendance_date),
    CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt hash, never plaintext
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL    -- ADMIN or STAFF
);

-- Sample/demo data is NOT loaded from SQL. The backend inserts it on first start (DataSeeder) with dates
-- relative to "today" so the dashboard is populated. Set SEED_ENABLED=false to skip it.
