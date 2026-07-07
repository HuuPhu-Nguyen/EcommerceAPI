ALTER TABLE user_model
    ADD COLUMN identity_subject VARCHAR(255);

UPDATE user_model
SET identity_subject = username
WHERE identity_subject IS NULL
  AND username IS NOT NULL;

CREATE UNIQUE INDEX ux_user_model_identity_subject
    ON user_model (identity_subject)
    WHERE identity_subject IS NOT NULL;
