CREATE TABLE customer (
                          id VARCHAR(64) NOT NULL,
                          CONSTRAINT pk_customer PRIMARY KEY (id)
);

-- existing attempts need their customer row before the foreign key can be added (no-op on a new database)
INSERT INTO customer (id) SELECT DISTINCT customer_id FROM load_attempt;

ALTER TABLE load_attempt ADD CONSTRAINT fk_load_attempt_customer
    FOREIGN KEY (customer_id) REFERENCES customer (id);
