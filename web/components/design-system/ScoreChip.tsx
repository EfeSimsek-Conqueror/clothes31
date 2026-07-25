import { clsx } from "clsx";

export function ScoreChip({
  score,
  className,
  size = "md",
}: {
  score: number | string;
  className?: string;
  size?: "sm" | "md" | "lg";
}) {
  const sizes: Record<string, string> = {
    sm: "h-8 min-w-12 text-sm",
    md: "h-10 min-w-14 text-base",
    lg: "h-12 min-w-16 text-lg",
  };
  return (
    <span
      className={clsx(
        "inline-flex items-center justify-center rounded-full bg-white text-ink serif px-3 shadow-[0_1px_2px_rgba(0,0,0,0.08)]",
        sizes[size],
        className,
      )}
    >
      {typeof score === "number" ? score.toFixed(1) : score}
    </span>
  );
}
