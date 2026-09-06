-- 0007_scoring_v4.sql
--
-- Scoring v4 — "the brief dial". The score stops being a plain mean of four
-- fixed subscores and becomes a weighted, capped headline over nine axes whose
-- weights are a function of what the wearer told us. These columns are what
-- makes that number auditable after the fact: the brief they set, the rubric it
-- produced, the axes and their weights, and the ordered list of rules that
-- fired with what each one cost.
--
-- `raw_axes` and `signals` are the model's untouched witness statement. They
-- exist so a re-score with a corrected brief can replay the whole pipeline with
-- no model call — free, sub-second, and identical arithmetic.

alter table public.outfits
  add column if not exists intake          jsonb  default '{}'::jsonb,
  add column if not exists rubric_id       text,
  add column if not exists rubric_version  int    default 1,
  add column if not exists scoring_version text   default 'v3_mean',
  add column if not exists axes            jsonb  default '[]'::jsonb,
  add column if not exists score_breakdown jsonb,
  add column if not exists dress_code      jsonb,
  add column if not exists presence_check  jsonb,
  add column if not exists lever           jsonb,
  add column if not exists caveats         jsonb  default '[]'::jsonb,
  add column if not exists pieces          jsonb  default '[]'::jsonb,
  add column if not exists signals         jsonb  default '{}'::jsonb,
  add column if not exists raw_axes        jsonb  default '{}'::jsonb,
  add column if not exists rescore_count   int    default 0;

comment on column public.outfits.intake is
  'The brief exactly as received: {occasion, formality 1-5, presence 1-5, role, venue, room, on_feet, intent, weather, time_of_day, has_back}.';
comment on column public.outfits.axes is
  'Ordered by weight desc: [{key,label,weight,raw_score,score,contribution,source,rank,evidence,capped_by?,ceiling?,ceiling_rule?,floored_by?,unjudgeable?}].';
comment on column public.outfits.score_breakdown is
  '{weights_source,null_axes[],discarded_axes[],weight_deltas[],raw_weighted,rules_fired[],caps_applied[],downgraded_to_caveat[],final,history[]}. rules_fired is an ordered waterfall: raw_weighted + Σ headline_cost = final.';
comment on column public.outfits.raw_axes is
  'The model''s untouched witness statement. Required by /rescore, which replays the engine over it with no model call.';
comment on column public.outfits.scoring_version is
  'v3_mean = the unweighted mean of four fixed subscores. v4 = the weighted, capped nine-axis headline. Averages and deltas must only ever be computed WITHIN a version — v4 caps only subtract, so a user''s first v4 score would otherwise read "below your average" for a look that is fine.';

create index if not exists outfits_rubric_idx   on public.outfits(user_id, rubric_id);
create index if not exists outfits_intake_gin   on public.outfits using gin (intake);
create index if not exists outfits_scorever_idx on public.outfits(user_id, scoring_version, created_at desc);

-- Label the history; do NOT recompute it. Rows scored before v4 have no intake
-- and no signals, so their headline cannot be re-derived — only mislabelled.
update public.outfits
   set scoring_version = 'v3_mean'
 where scoring_version is null;
