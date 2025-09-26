-- MySQL 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'lovepet'@'%' IDENTIFIED BY 'lovepet';
GRANT ALL PRIVILEGES ON *.* TO 'lovepet'@'%';
FLUSH PRIVILEGES;