-- 0008_tryon_before.sql
--
-- The photograph a try-on started from.
--
-- `photo_path` holds the composite — the person already wearing the piece — so
-- until now the original was uploaded, used, and thrown away. That made the
-- before/after comparison possible only in the seconds after a render, while
-- the source image was still in memory; a look reopened from the Journal could
-- only ever show the "after".
--
-- Try-on rows only. Everything else leaves it null, and a row written before
-- this column existed simply renders without the comparison.

alter table public.outfits
  add column if not exists before_photo_path text;

comment on column public.outfits.before_photo_path is
  'Storage path of the photograph a try-on was composited onto. Null for every other kind, and for try-ons saved before 2026-09-05.';
