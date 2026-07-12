DO $$
DECLARE
    duplicate_username TEXT;
    duplicate_email TEXT;
BEGIN
    SELECT lower(username)
    INTO duplicate_username
    FROM user_model
    WHERE username IS NOT NULL
    GROUP BY lower(username)
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF duplicate_username IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate normalized username exists before customer identity uniqueness migration: %',
            duplicate_username;
    END IF;

    SELECT lower(email)
    INTO duplicate_email
    FROM user_model
    WHERE email IS NOT NULL
    GROUP BY lower(email)
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF duplicate_email IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate normalized email exists before customer identity uniqueness migration: %',
            duplicate_email;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_model_identity_subject
    ON user_model (identity_subject)
    WHERE identity_subject IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_model_username_lower
    ON user_model (lower(username))
    WHERE username IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_model_email_lower
    ON user_model (lower(email))
    WHERE email IS NOT NULL;
