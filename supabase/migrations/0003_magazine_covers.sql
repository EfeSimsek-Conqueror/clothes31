create table if not exists public.magazine_covers (
  id uuid primary key default gen_random_uuid(),
  outfit_id uuid references public.outfits(id) on delete cascade,
  user_id uuid references auth.users(id) on delete cascade,
  headline text,
  pull_quote text,
  vol_number int,
  image_path text,
  created_at timestamptz default now()
);
alter table public.magazine_covers enable row level security;
drop policy if exists "own_covers" on public.magazine_covers;
create policy "own_covers" on public.magazine_covers
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create index if not exists magazine_covers_user_idx on public.magazine_covers(user_id);

-- Public storage bucket for finished covers (shareable URLs)
insert into storage.buckets (id, name, public)
  values ('magazine_covers', 'magazine_covers', true)
  on conflict (id) do nothing;
