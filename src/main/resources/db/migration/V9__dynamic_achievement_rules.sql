ALTER TABLE stickers
    ADD COLUMN rule_metric VARCHAR(64) NOT NULL DEFAULT 'LESSONS_COMPLETED',
    ADD COLUMN rule_target INT NOT NULL DEFAULT 1,
    ADD COLUMN rule_scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    ADD COLUMN rule_scope_id VARCHAR(36),
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE stickers SET is_active = FALSE;

WITH mapped_rules AS (
    SELECT DISTINCT ON (sticker.id)
        sticker.id AS sticker_id,
        track.id AS track_id,
        milestone.key::INT AS target
    FROM stickers sticker
    JOIN tracks track ON TRUE
    CROSS JOIN LATERAL jsonb_each_text(track.sticker_milestones) milestone
    WHERE milestone.value = sticker.id OR milestone.value = sticker.slug
    ORDER BY sticker.id, track.sort_order, milestone.key::INT
)
UPDATE stickers sticker
SET rule_metric = 'LESSONS_COMPLETED',
    rule_target = mapped_rules.target,
    rule_scope_type = 'TRACK',
    rule_scope_id = mapped_rules.track_id,
    is_active = TRUE
FROM mapped_rules
WHERE sticker.id = mapped_rules.sticker_id;

UPDATE stickers
SET rule_metric = 'LESSONS_COMPLETED',
    rule_target = 1,
    rule_scope_type = 'GLOBAL',
    rule_scope_id = NULL,
    is_active = TRUE
WHERE slug = 'first_step';

ALTER TABLE stickers
    ADD CONSTRAINT chk_sticker_rule_target CHECK (rule_target >= 1),
    ADD CONSTRAINT chk_sticker_rule_metric CHECK (rule_metric IN (
        'LESSONS_COMPLETED',
        'TOTAL_STARS',
        'TRACKS_COMPLETED',
        'SPECIFIC_LESSON_COMPLETED'
    )),
    ADD CONSTRAINT chk_sticker_rule_scope CHECK (
        (rule_scope_type = 'GLOBAL' AND rule_scope_id IS NULL) OR
        (rule_scope_type IN ('TRACK', 'LESSON') AND rule_scope_id IS NOT NULL)
    );

ALTER TABLE tracks DROP COLUMN sticker_milestones;

ALTER TABLE stickers RENAME TO achievements;
ALTER TABLE reward_wallet_stickers RENAME TO child_achievements;
ALTER TABLE child_achievements RENAME COLUMN sticker_id TO achievement_id;
ALTER TABLE child_achievements ADD COLUMN unlocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE reward_wallets RENAME COLUMN last_granted_sticker_id TO last_granted_achievement_id;
