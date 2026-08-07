-- 0005_stylist_memory.sql
-- Foundation for Stylist Chat (moderate AI) + passive wardrobe memory.
--
-- Tables:
--   chat_threads       – one thread per user (we keep a single rolling thread
--                        per user for now; multi-thread later if needed).
--   chat_messages      – transcript, one row per turn, with optional embedding
--                        for later semantic recall.
--   outfit_memory      – per-outfit summary the AI can retrieve to reference
--                        past looks ("last week's beige jacket…").
--   wardrobe_items     – deduplicated items extracted from outfits, with a
--                        canonical description + embedding + times_worn.
--   outfit_items       – many-to-many join (which items appeared in which
--                        outfit_memory).
--
-- pgvector is required (Supabase has it available; enabled here if missing).

create extension if not exists vector;

-- ----- chat threads ---------------------------------------------------------
create table if not exists chat_threads (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  title          text,
  created_at     timestamptz not null default now(),
  last_message_at timestamptz not null default now()
);
create index if not exists chat_threads_user_idx on chat_threads (user_id, last_message_at desc);

-- ----- chat messages --------------------------------------------------------
create table if not exists chat_messages (
  id             uuid primary key default gen_random_uuid(),
  thread_id      uuid not null references chat_threads(id) on delete cascade,
  user_id        uuid not null references auth.users(id) on delete cascade,
  role           text not null check (role in ('user','assistant','system')),
  content_text   text,
  image_urls     text[],
  embedding      vector(1536),
  created_at     timestamptz not null default now()
);
create index if not exists chat_messages_thread_idx on chat_messages (thread_id, created_at);
create index if not exists chat_messages_user_idx on chat_messages (user_id, created_at desc);

-- ----- outfit memory (RAG source) ------------------------------------------
create table if not exists outfit_memory (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users(id) on delete cascade,
  outfit_id         uuid,                       -- link back to outfits table if present
  image_url         text,
  worn_at           timestamptz not null default now(),
  ai_score          numeric,
  ai_comment        text,
  aesthetic_tags    text[] default '{}',
  occasion_vibes    text[] default '{}',
  embedding         vector(1536),
  created_at        timestamptz not null default now()
);
create index if not exists outfit_memory_user_idx on outfit_memory (user_id, worn_at desc);
-- ivfflat requires vector; keep as btree until enough rows exist for a good index.

-- ----- wardrobe items (dedup pool) -----------------------------------------
create table if not exists wardrobe_items (
  id                     uuid primary key default gen_random_uuid(),
  user_id                uuid not null references auth.users(id) on delete cascade,
  first_seen_outfit_id   uuid references outfit_memory(id) on delete set null,
  canonical_description  text,
  type                   text,            -- 'top','bottom','shoes','outerwear','accessory','fullbody'
  color                  text,
  material               text,
  embedding              vector(1536),
  times_worn             int not null default 1,
  last_worn_at           timestamptz,
  created_at             timestamptz not null default now()
);
create index if not exists wardrobe_items_user_idx on wardrobe_items (user_id, last_worn_at desc);

-- ----- join -----------------------------------------------------------------
create table if not exists outfit_items (
  outfit_id         uuid not null references outfit_memory(id) on delete cascade,
  wardrobe_item_id  uuid not null references wardrobe_items(id) on delete cascade,
  primary key (outfit_id, wardrobe_item_id)
);
create index if not exists outfit_items_item_idx on outfit_items (wardrobe_item_id);

-- ----- RLS: users see only their own data ----------------------------------
alter table chat_threads      enable row level security;
alter table chat_messages     enable row level security;
alter table outfit_memory     enable row level security;
alter table wardrobe_items    enable row level security;
alter table outfit_items      enable row level security;

do $$ begin
  create policy chat_threads_own on chat_threads
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

do $$ begin
  create policy chat_messages_own on chat_messages
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

do $$ begin
  create policy outfit_memory_own on outfit_memory
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

do $$ begin
  create policy wardrobe_items_own on wardrobe_items
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
exception when duplicate_object then null; end $$;

-- outfit_items policy: derive via parent outfit_memory ownership
do $$ begin
  create policy outfit_items_own on outfit_items
    for all
    using (exists (select 1 from outfit_memory o where o.id = outfit_items.outfit_id and o.user_id = auth.uid()))
    with check (exists (select 1 from outfit_memory o where o.id = outfit_items.outfit_id and o.user_id = auth.uid()));
exception when duplicate_object then null; end $$;

-- ----- similarity search helper (used later by chat RAG) --------------------
-- Cosine similarity retrieval; keep as a function so the client stays simple.
create or replace function match_outfit_memory (
  p_user_id     uuid,
  p_embedding   vector(1536),
  p_match_count int default 5
) returns table (
  id            uuid,
  ai_comment    text,
  image_url     text,
  worn_at       timestamptz,
  similarity    float
) language sql stable as $$
  select om.id, om.ai_comment, om.image_url, om.worn_at,
         1 - (om.embedding <=> p_embedding) as similarity
  from outfit_memory om
  where om.user_id = p_user_id and om.embedding is not null
  order by om.embedding <=> p_embedding
  limit p_match_count;
$$;
