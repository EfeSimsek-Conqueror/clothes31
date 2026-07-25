import { clsx } from "clsx";
import type { ReactNode } from "react";

export function PullQuote({
  children,
  attribution = "— Hem",
  className,
}: {
  children: ReactNode;
  attribution?: string;
  className?: string;
}) {
  return (
    <blockquote
      className={clsx(
        "serif italic text-ink text-[22px] leading-[1.35]",
        className,
      )}
    >
      <p className="mb-2">{children}</p>
      {attribution ? (
        <footer className="not-italic text-[13px] text-muted tracking-wide">
          {attribution}
        </footer>
      ) : null}
    </blockquote>
  );
}
