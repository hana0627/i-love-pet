-- MySQL 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'lovepet'@'%' IDENTIFIED BY 'lovepet';
GRANT ALL PRIVILEGES ON *.* TO 'lovepet'@'%';
FLUSH PRIVILEGES;

-- CREATE TABLE IF NOT EXIST users (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     name VARCHAR(50) NOT NULL,
--     email VARCHAR(100) NOT NULL,
--     phone_number VARCHAR(20),
--     created_at DATETIME NOT NULL
-- );
--
-- INSERT INTO users (id, name, email, phone_number, created_at) VALUES
-- (1, '박하나', 'hanana@lovepet.com', '01036066270','2024-07-01 10:00:00');
