create table if not exists public.body_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  torso_leg_ratio numeric,
  shoulder_hip_ratio numeric,
  body_shape text,           -- 'triangle' | 'inverted_triangle' | 'hourglass' | 'rectangle' | 'apple'
  skin_undertone text,       -- 'warm' | 'cool' | 'neutral'
  coloring_season text,      -- 'spring' | 'summer' | 'autumn' | 'winter'
  palette_hex text[] default '{}',   -- 6 hex chips
  notes text,
  front_photo_path text,
  side_photo_path text,
  updated_at timestamptz default now()
);
alter table public.body_profiles enable row level security;
create policy "own_body_profile" on public.body_profiles
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
