ALTER TABLE skills
    DROP COLUMN audio_url,
    DROP COLUMN illustration_url,
    ADD COLUMN required_successes INTEGER NOT NULL DEFAULT 2,
    ADD COLUMN min_accuracy_percent INTEGER NOT NULL DEFAULT 67,
    ADD COLUMN required_lesson_count INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_skills_required_successes CHECK (required_successes BETWEEN 1 AND 20),
    ADD CONSTRAINT chk_skills_min_accuracy CHECK (min_accuracy_percent BETWEEN 1 AND 100),
    ADD CONSTRAINT chk_skills_required_lessons CHECK (required_lesson_count BETWEEN 1 AND 20);

ALTER TABLE skill_progress
    ADD COLUMN practiced_lesson_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
