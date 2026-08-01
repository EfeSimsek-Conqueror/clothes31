alter table public.outfits
  add column if not exists intent text,
  add column if not exists back_photo_path text,
  add column if not exists fits_you numeric;

create table if not exists public.outfit_annotations (
  id uuid primary key default gen_random_uuid(),
  outfit_id uuid references public.outfits(id) on delete cascade,
  type text not null,
  coords jsonb not null,
  note text,
  confidence numeric,
  idx int
);
create index if not exists outfit_annotations_outfit_idx on public.outfit_annotations(outfit_id);
alter table public.outfit_annotations enable row level security;
drop policy if exists "own_annotations" on public.outfit_annotations;
create policy "own_annotations" on public.outfit_annotations
  for all using (
    exists(select 1 from public.outfits o where o.id = outfit_id and o.user_id = auth.uid())
  ) with check (
    exists(select 1 from public.outfits o where o.id = outfit_id and o.user_id = auth.uid())
  );

create table if not exists public.outfit_fit_maps (
  outfit_id uuid primary key references public.outfits(id) on delete cascade,
  resolution int[],
  grid jsonb,
  hotspots jsonb
);
alter table public.outfit_fit_maps enable row level security;
drop policy if exists "own_fit_map" on public.outfit_fit_maps;
create policy "own_fit_map" on public.outfit_fit_maps
  for all using (
    exists(select 1 from public.outfits o where o.id = outfit_id and o.user_id = auth.uid())
  ) with check (
    exists(select 1 from public.outfits o where o.id = outfit_id and o.user_id = auth.uid())
  );
