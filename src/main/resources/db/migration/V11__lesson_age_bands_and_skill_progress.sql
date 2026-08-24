ALTER TABLE lessons
    ADD COLUMN age_band VARCHAR(16) NOT NULL DEFAULT 'all';

ALTER TABLE lessons
    ADD CONSTRAINT chk_lessons_age_band CHECK (age_band IN ('all', '4-5', '6-8'));

CREATE TABLE skill_progress (
    child_id VARCHAR(36) NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    objective_id VARCHAR(128) NOT NULL,
    objective_title VARCHAR(255) NOT NULL,
    state VARCHAR(24) NOT NULL,
    successful_attempts INTEGER NOT NULL DEFAULT 0,
    total_attempts INTEGER NOT NULL DEFAULT 0,
    last_practiced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (child_id, objective_id),
    CONSTRAINT chk_skill_progress_state CHECK (state IN ('introduced', 'practicing', 'mastered', 'review_due'))
);

CREATE INDEX idx_skill_progress_child_state
    ON skill_progress(child_id, state, last_practiced_at);
