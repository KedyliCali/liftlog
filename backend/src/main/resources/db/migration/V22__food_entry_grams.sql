-- The portion in grams, when the user picked one (catalog food or scanned product, by grams or by servings).
-- Hand-typed entries and rows logged before this migration stay NULL: the amount is unknown, not zero.
-- Upper bound: a scanned pack serving can be up to 5000 g and the servings stepper goes to 10.
ALTER TABLE food_entries
    ADD COLUMN grams NUMERIC(7,1) CHECK (grams > 0 AND grams <= 50000);
