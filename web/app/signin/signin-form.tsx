"use client";

import { useState } from "react";
import { getBrowserSupabase } from "@/lib/supabase/browser";
import { PrimaryButton } from "@/components/design-system";

export function SigninForm() {
  const [mode, setMode] = useState<"idle" | "email">("idle");
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<null | string>(null);
  const [pending, setPending] = useState(false);

  async function google() {
    setPending(true);
    setStatus(null);
    try {
      const supabase = getBrowserSupabase();
      const { error } = await supabase.auth.signInWithOAuth({
        provider: "google",
        options: { redirectTo: `${location.origin}/app` },
      });
      if (error) setStatus(error.message);
    } finally {
      setPending(false);
    }
  }

  async function sendOtp(e: React.FormEvent) {
    e.preventDefault();
    setPending(true);
    setStatus(null);
    try {
      const supabase = getBrowserSupabase();
      const { error } = await supabase.auth.signInWithOtp({
        email,
        options: { emailRedirectTo: `${location.origin}/app` },
      });
      if (error) setStatus(error.message);
      else setStatus("Check your inbox for the sign-in link.");
    } finally {
      setPending(false);
    }
  }

  if (mode === "email") {
    return (
      <form onSubmit={sendOtp} className="space-y-3">
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@somewhere.com"
          className="w-full h-14 rounded-[4px] bg-card border border-ink/15 px-5 text-base placeholder:text-muted focus:outline-none focus:border-ink/60"
        />
        <PrimaryButton type="submit" full disabled={pending}>
          {pending ? "Sending…" : "Send magic link"}
        </PrimaryButton>
        <PrimaryButton type="button" variant="ghost" full onClick={() => setMode("idle")}>
          Back
        </PrimaryButton>
        {status ? <p className="text-sm text-muted text-center">{status}</p> : null}
      </form>
    );
  }

  return (
    <div className="space-y-3">
      <PrimaryButton onClick={google} full disabled={pending}>
        Continue with Google
      </PrimaryButton>
      <PrimaryButton variant="secondary" full onClick={() => setMode("email")}>
        Continue with email
      </PrimaryButton>
      {status ? <p className="text-sm text-muted text-center">{status}</p> : null}
    </div>
  );
}
