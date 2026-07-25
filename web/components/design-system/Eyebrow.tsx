import { clsx } from "clsx";
import type { ReactNode } from "react";

export function Eyebrow({
  children,
  className,
  as: Tag = "span",
}: {
  children: ReactNode;
  className?: string;
  as?: "span" | "div" | "p";
}) {
  return (
    <Tag
      className={clsx(
        "text-[0.7rem] uppercase tracking-[0.22em] text-bronze font-medium",
        className,
      )}
    >
      {children}
    </Tag>
  );
}
