DROP TABLE skill_progress;

CREATE TABLE skills (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    audio_url TEXT NOT NULL DEFAULT '',
    illustration_url TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE skill_progress (
    child_id VARCHAR(36) NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    skill_id VARCHAR(36) NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    skill_title VARCHAR(255) NOT NULL,
    state VARCHAR(24) NOT NULL,
    successful_attempts INTEGER NOT NULL DEFAULT 0,
    total_attempts INTEGER NOT NULL DEFAULT 0,
    last_practiced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (child_id, skill_id),
    CONSTRAINT chk_skill_progress_state CHECK (state IN ('introduced', 'practicing', 'mastered', 'review_due'))
);

CREATE INDEX idx_skill_progress_child_state
    ON skill_progress(child_id, state, last_practiced_at);
