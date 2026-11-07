CREATE TABLE load_attempt (
                              customer_id VARCHAR(64)              NOT NULL,
                              id          VARCHAR(64)              NOT NULL,
                              amount      DECIMAL(19, 2)           NOT NULL,
                              loaded_at   TIMESTAMP WITH TIME ZONE NOT NULL,
                              accepted    BOOLEAN                  NOT NULL,
                              CONSTRAINT pk_load_attempt PRIMARY KEY (customer_id, id)
);

CREATE INDEX idx_load_attempt_customer_loaded_at ON load_attempt (customer_id, loaded_at);
