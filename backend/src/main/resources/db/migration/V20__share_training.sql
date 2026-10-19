-- Opt-in for showing training summaries to the user's friend group (the League group). Off by default:
-- nothing about a user's training is visible to anyone else until they turn it on.
ALTER TABLE users ADD COLUMN share_training BOOLEAN NOT NULL DEFAULT false;
