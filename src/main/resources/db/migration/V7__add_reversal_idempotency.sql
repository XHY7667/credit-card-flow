ALTER TABLE authorization_reversals
    ADD COLUMN idempotency_key VARCHAR(100);

UPDATE authorization_reversals
SET idempotency_key = 'legacy-reversal-' || id
WHERE idempotency_key IS NULL;

ALTER TABLE authorization_reversals
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE authorization_reversals
    ADD CONSTRAINT uk_authorization_reversals_idempotency_key
        UNIQUE (idempotency_key);
