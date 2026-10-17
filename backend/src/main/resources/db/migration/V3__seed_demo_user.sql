-- Single demo user for the MVP; real sign-up arrives with JWT auth (v1.1).
-- '!' is not a valid BCrypt hash, so nobody can log in with this account until a password is set.
INSERT INTO users (email, password_hash, display_name)
VALUES ('demo@liftlog.app', '!', 'Demo User');
