ALTER TABLE children DROP CONSTRAINT IF EXISTS children_age_check;

UPDATE children SET age = 5 WHERE age <= 5;
UPDATE children SET age = 7 WHERE age >= 6;

ALTER TABLE children
    ADD CONSTRAINT children_age_check CHECK (age IN (5, 7));
