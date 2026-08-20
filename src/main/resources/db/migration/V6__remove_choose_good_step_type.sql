UPDATE lessons
SET steps = (
	SELECT jsonb_agg(
		CASE
			WHEN step->>'type' = 'choose_good'
				THEN jsonb_set(step, '{type}', '"show"'::jsonb)
			ELSE step
		END
		ORDER BY ordinal
	)
	FROM jsonb_array_elements(steps) WITH ORDINALITY AS items(step, ordinal)
)
WHERE steps @> '[{"type":"choose_good"}]'::jsonb;
