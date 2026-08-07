ALTER TABLE merchants
    ADD CONSTRAINT ck_merchants_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
