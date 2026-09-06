// deno-lint-ignore-file no-explicit-any
// Scoring v4 — normalising the model's witness statement.
//
// Lives outside index.ts so the test harness can replay a frozen JUDGE response
// through the engine without importing a module that calls Deno.serve.

import type { AxisKey } from "../_shared/rubric/types.ts";
import type { JudgeInput } from "./engine.ts";
import type { SignalMap } from "./rules.ts";

const MODEL_AXES: AxisKey[] = ["SIL", "FIT", "COL", "TEX", "DTL", "SHO", "PRS", "POV"];

/**
 * Signals arrive as a flat list of {key, value, confidence}. A union-typed
 * object is not expressible in a response schema, and one wrong-typed field
 * would fail the entire call — so everything travels as a string and is coerced
 * here.
 */
export function toSignalMap(judge: any): SignalMap {
  const out: SignalMap = {};
  const list = Array.isArray(judge?.signals_list) ? judge.signals_list : [];
  for (const s of list) {
    const key = String(s?.key ?? "").trim();
    if (!key) continue;
    out[key] = {
      value: String(s?.value ?? ""),
      confidence: Math.max(0, Math.min(1, Number(s?.confidence ?? 0.5))),
    };
  }
  // Accept the object form too, for a model that ignored the list instruction.
  const obj = judge?.signals;
  if (obj && typeof obj === "object" && !Array.isArray(obj)) {
    for (const [k, v] of Object.entries(obj)) {
      if (out[k]) continue;
      if (v && typeof v === "object" && "value" in (v as any)) {
        out[k] = {
          value: String((v as any).value),
          confidence: Math.max(0, Math.min(1, Number((v as any).confidence ?? 0.5))),
        };
      } else {
        out[k] = { value: String(v), confidence: 0.5 };
      }
    }
  }
  return out;
}

export function normalizeJudge(raw: any, palette: string[]): JudgeInput {
  const axes: JudgeInput["axes"] = {};
  const src = raw?.axes ?? {};
  for (const key of MODEL_AXES) {
    const a = src[key];
    if (!a) continue;
    const s = a.score;
    axes[key] = {
      score: s == null || !isFinite(Number(s)) ? null : Number(s),
      evidence: String(a.evidence ?? ""),
    };
  }
  return {
    axes,
    formality_read: Number(raw?.formality_read ?? 3),
    formality_evidence: String(raw?.formality_evidence ?? ""),
    presence_read: Number(raw?.presence_read ?? 3),
    presence_evidence: String(raw?.presence_evidence ?? ""),
    interp: Number(raw?.interp ?? 0),
    signals: toSignalMap(raw),
    intent_axis: raw?.intent_axis ?? null,
    intent_met: typeof raw?.intent_met === "boolean" ? raw.intent_met : null,
    intent_note: raw?.intent_note ?? null,
    caveats: Array.isArray(raw?.caveats) ? raw.caveats.map((c: unknown) => String(c)) : [],
    palette_hex: palette,
  };
}
