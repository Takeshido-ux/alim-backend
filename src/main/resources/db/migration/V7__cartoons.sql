CREATE TABLE cartoon_tags (
	id VARCHAR(36) PRIMARY KEY,
	title TEXT NOT NULL,
	icon TEXT NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cartoons (
	id VARCHAR(36) PRIMARY KEY,
	title TEXT NOT NULL,
	description TEXT NOT NULL DEFAULT '',
	img TEXT NOT NULL DEFAULT '',
	video TEXT NOT NULL DEFAULT '',
	tag_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
	episodes JSONB NOT NULL DEFAULT '[]'::jsonb,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cartoon_favorites (
	child_id VARCHAR(36) NOT NULL REFERENCES children (id) ON DELETE CASCADE,
	cartoon_id VARCHAR(36) NOT NULL REFERENCES cartoons (id) ON DELETE CASCADE,
	PRIMARY KEY (child_id, cartoon_id)
);
