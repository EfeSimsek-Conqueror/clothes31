// deno-lint-ignore-file no-explicit-any
Deno.serve(async (req: Request) => {
  const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  };
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401,
      headers: { ...corsHeaders, "content-type": "application/json" },
    });
  }

  const FAL_KEY = Deno.env.get("FAL_KEY");
  if (!FAL_KEY) {
    return new Response(JSON.stringify({ error: "server_misconfigured" }), {
      status: 500,
      headers: { ...corsHeaders, "content-type": "application/json" },
    });
  }

  let body: any = {};
  try { body = await req.json(); } catch { /* ignore */ }
  const prompt = (body?.prompt ?? "").toString().trim();
  if (!prompt) {
    return new Response(JSON.stringify({ error: "missing_prompt" }), {
      status: 400,
      headers: { ...corsHeaders, "content-type": "application/json" },
    });
  }

  const rawUrls = body?.image_urls;
  const imageUrls: string[] = Array.isArray(rawUrls)
    ? rawUrls.filter((u: any) => typeof u === "string" && u.length > 0)
    : [];

  const useEdit = imageUrls.length > 0;
  const endpoint = useEdit
    ? "https://fal.run/fal-ai/nano-banana/edit"
    : "https://fal.run/fal-ai/nano-banana";
  const falPayload: Record<string, unknown> = {
    prompt,
    num_images: 1,
    output_format: "png",
    sync_mode: false,
  };
  if (useEdit) falPayload.image_urls = imageUrls;

  const falResp = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Authorization": `Key ${FAL_KEY}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(falPayload),
  });
  if (!falResp.ok) {
    const txt = await falResp.text();
    return new Response(JSON.stringify({ error: "fal_error", detail: txt }), {
      status: 502,
      headers: { ...corsHeaders, "content-type": "application/json" },
    });
  }
  const json: any = await falResp.json();
  const image_url = json?.images?.[0]?.url ?? json?.image?.url ?? null;
  if (!image_url) {
    return new Response(JSON.stringify({ error: "no_image", raw: json }), {
      status: 502,
      headers: { ...corsHeaders, "content-type": "application/json" },
    });
  }
  return new Response(JSON.stringify({ image_url, seed: null }), {
    headers: { ...corsHeaders, "content-type": "application/json" },
  });
});
