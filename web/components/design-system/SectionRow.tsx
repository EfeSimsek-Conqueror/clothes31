import { clsx } from "clsx";
import type { ReactNode } from "react";

export function SectionRow({
  title,
  subtitle,
  right,
  leading,
  className,
  onClick,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  right?: ReactNode;
  leading?: ReactNode;
  className?: string;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className={clsx(
        "flex items-center gap-4 py-4 border-b hairline",
        onClick && "cursor-pointer",
        className,
      )}
    >
      {leading ? <div className="flex-none">{leading}</div> : null}
      <div className="flex-1 min-w-0">
        <div className="serif text-[17px] leading-tight text-ink truncate">
          {title}
        </div>
        {subtitle ? (
          <div className="text-[13px] text-muted mt-0.5 truncate">{subtitle}</div>
        ) : null}
      </div>
      {right ? <div className="flex-none">{right}</div> : null}
    </div>
  );
}

export function SectionPill({
  children,
  tone = "bronze",
}: {
  children: ReactNode;
  tone?: "bronze" | "ink" | "muted";
}) {
  const tones: Record<string, string> = {
    bronze: "bg-bronze/10 text-bronze",
    ink: "bg-ink text-white",
    muted: "bg-ink/5 text-muted",
  };
  return (
    <span
      className={clsx(
        "inline-flex items-center h-6 px-2 rounded-full text-[0.62rem] uppercase tracking-[0.18em] font-medium",
        tones[tone],
      )}
    >
      {children}
    </span>
  );
}
