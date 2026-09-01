-- 0006_content_reports.sql
-- In-app reporting of AI-generated output. Play's AI-Generated Content policy
-- requires the report affordance to live on the artifact itself, so every
-- generated surface writes here.

create table if not exists public.content_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  content_kind text not null,   -- 'outfit' | 'studio_piece' | 'cover' | 'chat_message' | 'invitation' | 'letter'
  content_id text not null,     -- text, not uuid: some artifacts are addressed by non-uuid ids
  reason text not null check (reason in ('offensive','sexual','violent','misleading','other')),
  note text,
  created_at timestamptz not null default now()
);
create index if not exists content_reports_created_idx on public.content_reports(created_at desc);

alter table public.content_reports enable row level security;
-- Write-only from the client: a reporter may file, nobody may read back. Moderation
-- reads happen with the service role, so there is deliberately no select policy.
drop policy if exists "insert_own_report" on public.content_reports;
create policy "insert_own_report" on public.content_reports
  for insert to authenticated with check (auth.uid() = user_id);
