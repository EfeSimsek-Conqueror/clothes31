import { getServerSupabase } from "./supabase/server";

export type Outfit = {
  id: string;
  user_id: string;
  name: string | null;
  score: number | null;
  image_url: string | null;
  verdict: string | null;
  created_at: string;
  notes: string | null;
};

export type ClosetItem = {
  id: string;
  user_id: string;
  name: string | null;
  category: string | null;
  color: string | null;
  image_url: string | null;
};

export type SundayLetter = {
  id: string;
  user_id: string;
  week_start: string;
  body: string;
  variant: string | null;
};

export async function getCurrentUser() {
  const supabase = await getServerSupabase();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  return user;
}

export async function fetchOutfits(limit = 20): Promise<Outfit[]> {
  const supabase = await getServerSupabase();
  const { data, error } = await supabase
    .from("outfits")
    .select("id,user_id,name,score,image_url,verdict,created_at,notes")
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) return [];
  return (data as Outfit[]) ?? [];
}

export async function fetchLatestOutfit(): Promise<Outfit | null> {
  const outfits = await fetchOutfits(1);
  return outfits[0] ?? null;
}

export async function fetchClosetItems(limit = 30): Promise<ClosetItem[]> {
  const supabase = await getServerSupabase();
  const { data, error } = await supabase
    .from("closet_items")
    .select("id,user_id,name,category,color,image_url")
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) return [];
  return (data as ClosetItem[]) ?? [];
}

export async function fetchLatestSundayLetter(): Promise<SundayLetter | null> {
  const supabase = await getServerSupabase();
  const { data, error } = await supabase
    .from("sunday_letters")
    .select("id,user_id,week_start,body,variant")
    .order("week_start", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) return null;
  return (data as SundayLetter) ?? null;
}

export async function fetchCreditsBalance(): Promise<number> {
  const supabase = await getServerSupabase();
  const { data } = await supabase
    .from("credit_transactions")
    .select("amount");
  if (!data) return 240; // placeholder default
  return data.reduce((sum, row: { amount: number }) => sum + (row.amount ?? 0), 240);
}

export async function fetchOutfitById(id: string): Promise<Outfit | null> {
  const supabase = await getServerSupabase();
  const { data, error } = await supabase
    .from("outfits")
    .select("id,user_id,name,score,image_url,verdict,created_at,notes")
    .eq("id", id)
    .maybeSingle();
  if (error) return null;
  return (data as Outfit) ?? null;
}
