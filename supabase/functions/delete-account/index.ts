// deno-lint-ignore-file no-explicit-any
// delete-account v1: hard account deletion for App Store guideline 5.1.1(v).
//
// The client used to sweep a handful of tables itself and then sign out — the
// auth.users row survived, so the same Apple/Google/email identity signed
// straight back in. Everything now happens here, with the service role:
//   1. resolve the caller from their JWT (never from the request body),
//   2. delete every user-owned row across public tables,
//   3. empty every storage prefix the user owns (including the PUBLIC
//      magazine_covers bucket, which is recursive: <uid>/ and <uid>/templates/),
//   4. revoke the Sign in with Apple token,
//   5. auth.admin.deleteUser(uid) — the step that makes re-sign-in fail.
//
// ⚠️ APPLE REVOCATION IS INERT UNTIL SECRETS ARE SET. Apple requires token
// revocation from apps that offer Sign in with Apple + account deletion. It
// needs a client secret JWT signed with an Apple .p8 key, which has NOT been
// supplied yet. Set all four of APPLE_SIWA_PRIVATE_KEY (the .p8 PEM),
// APPLE_KEY_ID, APPLE_TEAM_ID and APPLE_CLIENT_ID (the app bundle id) as
// function secrets to switch it on. While they are absent this function logs
// `apple_revoke_skipped` and continues with deletion rather than failing the
// whole request — data still goes away, the Apple grant just lingers.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

// Every public table that holds user-owned rows, child-first. Most have
// `on delete cascade` off auth.users, but we do not rely on that — a missing
// cascade would leave orphans behind and Apple's reviewer checks the data is
// gone, not the FK graph. `col` is the column holding the user id.
// `magazine_covers` covers BOTH finished covers (outfit_id set) and the
// cover templates from create-cover-template (outfit_id is null).
const USER_TABLES: Array<{ table: string; col: string }> = [
  { table: "outfit_annotations", col: "" }, // via outfits, see deleteOutfitChildren
  { table: "outfit_fit_maps", col: "" }, // via outfits, see deleteOutfitChildren
  { table: "chat_messages", col: "user_id" },
  { table: "chat_threads", col: "user_id" },
  { table: "outfit_items", col: "" }, // via outfit_memory cascade
  { table: "wardrobe_items", col: "user_id" },
  { table: "outfit_memory", col: "user_id" },
  { table: "magazine_covers", col: "user_id" },
  { table: "invitation_reads", col: "user_id" },
  { table: "body_profiles", col: "user_id" },
  { table: "content_reports", col: "user_id" },
  { table: "push_settings", col: "user_id" },
  { table: "sunday_letters", col: "user_id" },
  { table: "hem_notes", col: "user_id" },
  { table: "credit_transactions", col: "user_id" },
  { table: "closet_items", col: "user_id" },
  { table: "outfits", col: "user_id" },
  { table: "profiles", col: "id" },
];

// Buckets that key objects under a `<uid>/` prefix. `references` is legacy —
// it may not exist on every project; a missing bucket is not an error here.
const BUCKETS = ["avatars", "closet", "outfits", "references", "magazine_covers"];

// Postgres "relation does not exist" / "column does not exist". A project that
// never ran a given migration must not fail the whole deletion.
function isMissingSchema(err: any): boolean {
  const code = String(err?.code ?? "");
  if (code === "42P01" || code === "42703") return true;
  const msg = String(err?.message ?? "").toLowerCase();
  return msg.includes("does not exist") || msg.includes("could not find the table");
}

/// outfit_annotations / outfit_fit_maps / outfit_items are keyed by their
/// parent, not by user_id. Clear them explicitly before the parent rows go.
async function deleteOutfitChildren(admin: any, uid: string, failures: string[]) {
  const { data: outfits } = await admin.from("outfits").select("id").eq("user_id", uid);
  const outfitIds: string[] = Array.isArray(outfits) ? outfits.map((o: any) => o.id) : [];
  if (outfitIds.length > 0) {
    for (const table of ["outfit_annotations", "outfit_fit_maps"]) {
      const { error } = await admin.from(table).delete().in("outfit_id", outfitIds);
      if (error && !isMissingSchema(error)) failures.push(`${table}: ${error.message}`);
    }
  }

  const { data: memories } = await admin.from("outfit_memory").select("id").eq("user_id", uid);
  const memoryIds: string[] = Array.isArray(memories) ? memories.map((m: any) => m.id) : [];
  if (memoryIds.length > 0) {
    const { error } = await admin.from("outfit_items").delete().in("outfit_id", memoryIds);
    if (error && !isMissingSchema(error)) failures.push(`outfit_items: ${error.message}`);
  }
}

/// Storage has no recursive delete. List the prefix, remove the files, recurse
/// into folders (list() returns folders with a null id).
async function purgePrefix(admin: any, bucket: string, prefix: string, depth = 0): Promise<number> {
  if (depth > 3) return 0;
  const { data, error } = await admin.storage.from(bucket).list(prefix, { limit: 1000 });
  if (error || !Array.isArray(data)) return 0;

  const files: string[] = [];
  const folders: string[] = [];
  for (const entry of data) {
    const name = String(entry?.name ?? "");
    if (!name) continue;
    if (entry?.id) files.push(`${prefix}${name}`);
    else folders.push(`${prefix}${name}/`);
  }

  let removed = 0;
  if (files.length > 0) {
    const { error: rmErr } = await admin.storage.from(bucket).remove(files);
    if (!rmErr) removed += files.length;
  }
  for (const folder of folders) {
    removed += await purgePrefix(admin, bucket, folder, depth + 1);
  }
  return removed;
}

// ---------------------------------------------------------------------------
// Sign in with Apple revocation
// ---------------------------------------------------------------------------

function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToPkcs8(pem: string): Uint8Array {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/g, "")
    .replace(/-----END [^-]+-----/g, "")
    .replace(/\s+/g, "");
  const raw = atob(body);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

/// Build the ES256 client-secret JWT Apple's token endpoints require.
async function appleClientSecret(
  privateKeyPem: string,
  keyId: string,
  teamId: string,
  clientId: string,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToPkcs8(privateKeyPem),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "ES256", kid: keyId, typ: "JWT" };
  const payload = {
    iss: teamId,
    iat: now,
    exp: now + 300,
    aud: "https://appleid.apple.com",
    sub: clientId,
  };
  const enc = new TextEncoder();
  const signingInput =
    `${b64url(enc.encode(JSON.stringify(header)))}.${b64url(enc.encode(JSON.stringify(payload)))}`;
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, enc.encode(signingInput)),
  );
  return `${signingInput}.${b64url(sig)}`;
}

/// Returns a short status string for the response so the reviewer (and we) can
/// tell revocation apart from a silent no-op.
async function revokeAppleToken(user: any, bodyToken: string | null): Promise<string> {
  const privateKey = Deno.env.get("APPLE_SIWA_PRIVATE_KEY");
  const keyId = Deno.env.get("APPLE_KEY_ID");
  const teamId = Deno.env.get("APPLE_TEAM_ID");
  const clientId = Deno.env.get("APPLE_CLIENT_ID");

  const identities: any[] = Array.isArray(user?.identities) ? user.identities : [];
  const apple = identities.find((i: any) => String(i?.provider ?? "") === "apple");
  if (!apple) return "not_apple";

  if (!privateKey || !keyId || !teamId || !clientId) {
    // Inert until the .p8 and its ids are supplied. Deletion continues.
    console.warn("apple_revoke_skipped: APPLE_SIWA_PRIVATE_KEY/APPLE_KEY_ID/APPLE_TEAM_ID/APPLE_CLIENT_ID not set");
    return "skipped_no_secrets";
  }

  // The token to revoke: whatever the client held (its own refresh token from
  // the authorization-code exchange), else anything Supabase stashed on the
  // identity row.
  const token = bodyToken ||
    String(apple?.identity_data?.refresh_token ?? "") ||
    String(apple?.identity_data?.access_token ?? "");
  const hint = (bodyToken || apple?.identity_data?.refresh_token) ? "refresh_token" : "access_token";
  if (!token) {
    console.warn("apple_revoke_skipped: no apple refresh/access token on file for user");
    return "skipped_no_token";
  }

  try {
    const secret = await appleClientSecret(privateKey, keyId, teamId, clientId);
    const form = new URLSearchParams({
      client_id: clientId,
      client_secret: secret,
      token,
      token_type_hint: hint,
    });
    const resp = await fetch("https://appleid.apple.com/auth/revoke", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: form.toString(),
    });
    if (!resp.ok) {
      const detail = (await resp.text()).slice(0, 200);
      console.error("apple_revoke_failed", resp.status, detail);
      return `failed_${resp.status}`;
    }
    return "revoked";
  } catch (e) {
    console.error("apple_revoke_error", String(e).slice(0, 200));
    return "error";
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return json(401, { error: "unauthorized" });
  }

  const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!SUPABASE_URL || !SERVICE_ROLE) {
    return json(500, { error: "server_misconfigured" });
  }

  let body: any = {};
  try { body = await req.json(); } catch { /* noop */ }
  // Only ever used as the caller's OWN Apple token. The user id is never read
  // from the body — that would let anyone delete anyone.
  const appleRefreshToken = body?.apple_refresh_token
    ? String(body.apple_refresh_token).trim() || null
    : null;

  const userClient = createClient(SUPABASE_URL, SERVICE_ROLE, {
    global: { headers: { Authorization: auth } },
  });
  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData?.user?.id) {
    return json(401, { error: "unauthorized", detail: userErr?.message });
  }
  const user = userData.user;
  const uid = user.id;

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE);

  // ---------- 1. rows ----------
  const failures: string[] = [];
  await deleteOutfitChildren(admin, uid, failures);
  for (const { table, col } of USER_TABLES) {
    if (!col) continue; // handled by deleteOutfitChildren
    const { error } = await admin.from(table).delete().eq(col, uid);
    if (error && !isMissingSchema(error)) failures.push(`${table}: ${error.message}`);
  }

  // ---------- 2. storage ----------
  let objectsRemoved = 0;
  for (const bucket of BUCKETS) {
    objectsRemoved += await purgePrefix(admin, bucket, `${uid}/`);
  }

  // ---------- 3. Sign in with Apple ----------
  const appleStatus = await revokeAppleToken(user, appleRefreshToken);

  // ---------- 4. the auth row itself ----------
  const { error: delErr } = await admin.auth.admin.deleteUser(uid);
  if (delErr) {
    console.error("auth_delete_failed", delErr.message);
    return json(500, {
      error: "auth_delete_failed",
      detail: delErr.message,
      table_failures: failures,
      objects_removed: objectsRemoved,
      apple_revocation: appleStatus,
    });
  }

  console.log("account_deleted", JSON.stringify({
    uid,
    objects_removed: objectsRemoved,
    table_failures: failures.length,
    apple_revocation: appleStatus,
  }));

  return json(200, {
    deleted: true,
    objects_removed: objectsRemoved,
    table_failures: failures,
    apple_revocation: appleStatus,
  });
});
