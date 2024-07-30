CREATE DATABASE pricegrabber;

USE pricegrabber;

CREATE TABLE products
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    price       DOUBLE       NOT NULL,
    description TEXT,
    image_path  VARCHAR(255),
    create_date DATE,
    available   BOOLEAN
);
