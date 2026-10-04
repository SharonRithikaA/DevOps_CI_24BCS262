-- Gym Membership Management System - one-time MySQL setup
-- Run as a MySQL administrator:   mysql -u root -p < database/setup.sql
-- Replace the password below with your own, and use the same value for DB_PASSWORD in backend/.env

CREATE DATABASE IF NOT EXISTS gym_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'gym_user'@'localhost' IDENTIFIED BY 'change_me_db_password';
GRANT ALL PRIVILEGES ON gym_db.* TO 'gym_user'@'localhost';
FLUSH PRIVILEGES;
