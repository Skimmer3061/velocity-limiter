CREATE TABLE load_attempt_violation (
                                        customer_id VARCHAR(64) NOT NULL,
                                        load_id     VARCHAR(64) NOT NULL,
                                        rule        VARCHAR(32) NOT NULL,
                                        CONSTRAINT pk_load_attempt_violation PRIMARY KEY (customer_id, load_id, rule),
                                        CONSTRAINT fk_load_attempt_violation_attempt FOREIGN KEY (customer_id, load_id)
                                            REFERENCES load_attempt (customer_id, id)
);
