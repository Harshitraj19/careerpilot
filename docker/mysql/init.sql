-- One database per microservice (database-per-service pattern).
CREATE DATABASE IF NOT EXISTS placify_auth;
CREATE DATABASE IF NOT EXISTS placify_profile;
CREATE DATABASE IF NOT EXISTS placify_placement;
CREATE DATABASE IF NOT EXISTS placify_coding;
CREATE DATABASE IF NOT EXISTS placify_submission;
CREATE DATABASE IF NOT EXISTS placify_assessment;
CREATE DATABASE IF NOT EXISTS placify_proctoring;
CREATE DATABASE IF NOT EXISTS placify_ai;
CREATE DATABASE IF NOT EXISTS placify_interview;
CREATE DATABASE IF NOT EXISTS placify_notification;
CREATE DATABASE IF NOT EXISTS placify_analytics;

GRANT ALL PRIVILEGES ON `placify\_%`.* TO 'placify'@'%';
FLUSH PRIVILEGES;
