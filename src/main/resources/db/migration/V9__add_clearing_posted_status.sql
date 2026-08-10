ALTER TABLE clearings
    DROP CONSTRAINT ck_clearings_status;

ALTER TABLE clearings
    ADD CONSTRAINT ck_clearings_status
        CHECK (status IN ('PENDING', 'POSTED'));
