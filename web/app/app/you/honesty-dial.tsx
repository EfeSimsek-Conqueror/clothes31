"use client";
import { useState } from "react";
import { clsx } from "clsx";

const modes = ["Kind", "Honest", "Brutal"] as const;

export function HonestyDial() {
  const [mode, setMode] = useState<(typeof modes)[number]>("Honest");
  return (
    <div className="mt-4 grid grid-cols-3 gap-1 p-1 rounded-[4px] bg-ink/5">
      {modes.map((m) => (
        <button
          key={m}
          onClick={() => setMode(m)}
          className={clsx(
            "h-10 rounded-[4px] text-xs uppercase tracking-[0.18em] font-medium transition-colors",
            mode === m ? "seg-active" : "text-muted",
          )}
        >
          {m}
        </button>
      ))}
    </div>
  );
}
