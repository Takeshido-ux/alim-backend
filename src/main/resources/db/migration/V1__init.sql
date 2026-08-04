CREATE TABLE parent_accounts (
	id VARCHAR(36) PRIMARY KEY,
	phone_number VARCHAR(32) NOT NULL UNIQUE,
	pin_hash TEXT NOT NULL,
	active_child_id VARCHAR(36),
	ui_language VARCHAR(16) NOT NULL DEFAULT 'ru',
	voice_language VARCHAR(16) NOT NULL DEFAULT 'ru',
	reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE,
	daily_lesson_goal INT NOT NULL DEFAULT 1 CHECK (daily_lesson_goal BETWEEN 1 AND 3)
);

CREATE TABLE children (
	id VARCHAR(36) PRIMARY KEY,
	parent_id VARCHAR(36) NOT NULL REFERENCES parent_accounts (id) ON DELETE CASCADE,
	name VARCHAR(24) NOT NULL,
	age INT NOT NULL CHECK (age IN (5, 6)),
	avatar_id VARCHAR(32) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	archived_at TIMESTAMPTZ
);

CREATE INDEX idx_children_parent_active ON children (parent_id) WHERE archived_at IS NULL;

CREATE TABLE tracks (
	id VARCHAR(36) PRIMARY KEY,
	slug VARCHAR(128) NOT NULL UNIQUE,
	sort_order INT NOT NULL CHECK (sort_order >= 1),
	title TEXT NOT NULL,
	sticker_milestones JSONB NOT NULL DEFAULT '{}'::jsonb,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE lessons (
	id VARCHAR(36) PRIMARY KEY,
	slug VARCHAR(128) NOT NULL UNIQUE,
	track_id VARCHAR(36) NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
	order_in_track INT NOT NULL CHECK (order_in_track >= 1),
	title TEXT NOT NULL,
	parent_note TEXT NOT NULL,
	content_version VARCHAR(64) NOT NULL DEFAULT '1',
	steps JSONB NOT NULL DEFAULT '[]'::jsonb,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_lessons_track_order ON lessons (track_id, order_in_track);

CREATE TABLE stickers (
	id VARCHAR(36) PRIMARY KEY,
	slug VARCHAR(128) NOT NULL UNIQUE,
	title TEXT NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE media_assets (
	id VARCHAR(36) PRIMARY KEY,
	original_filename TEXT NOT NULL,
	content_type VARCHAR(128) NOT NULL,
	size_bytes BIGINT NOT NULL,
	storage_key TEXT NOT NULL UNIQUE,
	created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE lesson_progress (
	child_id VARCHAR(36) NOT NULL REFERENCES children (id) ON DELETE CASCADE,
	lesson_id VARCHAR(36) NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
	status VARCHAR(32) NOT NULL,
	current_step_index INT NOT NULL DEFAULT 0,
	completed_step_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
	attempt_count INT NOT NULL DEFAULT 0,
	first_try_practice_correct BOOLEAN NOT NULL DEFAULT TRUE,
	incorrect_practice_retries INT NOT NULL DEFAULT 0,
	stars_earned INT NOT NULL DEFAULT 0,
	started_at TIMESTAMPTZ,
	completed_at TIMESTAMPTZ,
	updated_at TIMESTAMPTZ NOT NULL,
	content_version_at_start VARCHAR(64) NOT NULL,
	PRIMARY KEY (child_id, lesson_id)
);

CREATE TABLE reward_wallets (
	child_id VARCHAR(36) PRIMARY KEY REFERENCES children (id) ON DELETE CASCADE,
	total_stars INT NOT NULL DEFAULT 0,
	last_granted_sticker_id VARCHAR(36) REFERENCES stickers (id) ON DELETE SET NULL
);

CREATE TABLE reward_wallet_stickers (
	child_id VARCHAR(36) NOT NULL REFERENCES reward_wallets (child_id) ON DELETE CASCADE,
	sticker_id VARCHAR(36) NOT NULL REFERENCES stickers (id) ON DELETE CASCADE,
	PRIMARY KEY (child_id, sticker_id)
);
