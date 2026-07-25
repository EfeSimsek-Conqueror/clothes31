"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { clsx } from "clsx";
import { Eyebrow, PrimaryButton, SerifDisplay } from "@/components/design-system";

const genders = [
  { key: "female", label: "Female" },
  { key: "male", label: "Male" },
  { key: "other", label: "Other" },
];

const vibes = [
  "minimal",
  "streetwear",
  "classic",
  "romantic",
  "sporty",
  "bold prints",
  "earth tones",
  "monochrome",
];

export function OnboardingFlow() {
  const router = useRouter();
  const [step, setStep] = useState<1 | 2>(1);
  const [gender, setGender] = useState<string | null>(null);
  const [picked, setPicked] = useState<string[]>([]);

  function toggle(v: string) {
    setPicked((prev) =>
      prev.includes(v) ? prev.filter((x) => x !== v) : [...prev, v],
    );
  }

  return (
    <div className="flex flex-col h-full">
      <div className="px-8 pt-12">
        <Eyebrow>Step {step} of 2</Eyebrow>
        {step === 1 ? (
          <SerifDisplay size="xl" className="mt-3">
            Who&apos;s dressing?
          </SerifDisplay>
        ) : (
          <SerifDisplay size="xl" className="mt-3">
            Your vibe.
          </SerifDisplay>
        )}
        <p className="text-muted mt-3">
          {step === 1
            ? "Hem calibrates for silhouette and fit."
            : "Pick as many as feel true today."}
        </p>
      </div>

      <div className="flex-1 overflow-y-auto px-8 mt-8">
        {step === 1 ? (
          <div className="space-y-3">
            {genders.map((g) => (
              <button
                key={g.key}
                onClick={() => setGender(g.key)}
                className={clsx(
                  "w-full h-16 rounded-[4px] border text-left px-5 serif text-lg transition-colors",
                  gender === g.key
                    ? "border-ink bg-card"
                    : "border-ink/15 hover:border-ink/40",
                )}
              >
                {g.label}
              </button>
            ))}
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {vibes.map((v) => {
              const on = picked.includes(v);
              return (
                <button
                  key={v}
                  onClick={() => toggle(v)}
                  className={clsx(
                    "px-4 h-10 rounded-full text-sm border transition-colors",
                    on
                      ? "bg-ink text-white border-ink"
                      : "bg-transparent text-ink border-ink/20 hover:border-ink/50",
                  )}
                >
                  {v}
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="sticky bottom-0 px-8 pt-6 pb-8 bg-paper border-t hairline">
        {step === 1 ? (
          <PrimaryButton
            full
            disabled={!gender}
            onClick={() => setStep(2)}
            className={clsx(!gender && "opacity-40")}
          >
            Continue
          </PrimaryButton>
        ) : (
          <PrimaryButton
            full
            disabled={picked.length === 0}
            onClick={() => router.push("/paywall")}
            className={clsx(picked.length === 0 && "opacity-40")}
          >
            Continue — {picked.length} picked
          </PrimaryButton>
        )}
      </div>
    </div>
  );
}
