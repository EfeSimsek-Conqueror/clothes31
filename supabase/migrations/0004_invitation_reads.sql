create table if not exists public.invitation_reads (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade,
  image_path text,
  event_type text,
  dress_code text,
  time_of_day text,
  venue text,
  notes text,
  suggested_combos jsonb,
  created_at timestamptz default now()
);
alter table public.invitation_reads enable row level security;
create policy "own_invitation_reads" on public.invitation_reads
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
