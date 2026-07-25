import { clsx } from "clsx";
import type { ReactNode } from "react";

export function SerifDisplay({
  children,
  className,
  as: Tag = "h1",
  size = "xl",
}: {
  children: ReactNode;
  className?: string;
  as?: "h1" | "h2" | "h3" | "p" | "div";
  size?: "sm" | "md" | "lg" | "xl" | "hero";
}) {
  const sizes: Record<string, string> = {
    sm: "text-2xl leading-[1.05]",
    md: "text-3xl leading-[1.05]",
    lg: "text-4xl leading-[1.02]",
    xl: "text-[42px] leading-[1.02]",
    hero: "text-[52px] sm:text-[64px] leading-[0.98]",
  };
  return (
    <Tag
      className={clsx(
        "serif tracking-[-0.01em] text-ink",
        sizes[size],
        className,
      )}
    >
      {children}
    </Tag>
  );
}
