CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price BIGINT NOT NULL,
    stock INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

INSERT INTO products (name, price, stock, created_at, updated_at) VALUES
    ('로얄캐닌 고양이 사료', 35000, 1000, '2024-07-01 10:00:00', '2024-07-01 10:00:00');