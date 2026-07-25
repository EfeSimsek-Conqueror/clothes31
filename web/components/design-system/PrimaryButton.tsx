import { clsx } from "clsx";
import Link from "next/link";
import type { ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost";

const base =
  "inline-flex items-center justify-center h-14 rounded-[4px] text-sm uppercase tracking-[0.14em] font-medium transition-colors select-none whitespace-nowrap";

const variants: Record<Variant, string> = {
  primary: "bg-ink text-white hover:bg-black active:bg-black",
  secondary:
    "bg-transparent text-ink border border-ink/25 hover:border-ink/60",
  ghost: "bg-transparent text-ink hover:bg-ink/5",
};

type Props = {
  children: ReactNode;
  variant?: Variant;
  className?: string;
  full?: boolean;
};

export function PrimaryButton({
  children,
  variant = "primary",
  className,
  full,
  ...rest
}: Props & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      className={clsx(base, variants[variant], full && "w-full", "px-6", className)}
      {...rest}
    >
      {children}
    </button>
  );
}

export function PrimaryLink({
  children,
  href,
  variant = "primary",
  className,
  full,
}: Props & { href: string }) {
  return (
    <Link
      href={href}
      className={clsx(base, variants[variant], full && "w-full", "px-6", className)}
    >
      {children}
    </Link>
  );
}
