"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { clsx } from "clsx";
import { Camera } from "lucide-react";

const tabs = [
  { href: "/app", label: "Today" },
  { href: "/app/studio", label: "Studio" },
  { href: "/app/journal", label: "Journal" },
  { href: "/app/you", label: "You" },
];

export function BottomTabBar() {
  const pathname = usePathname();
  const isActive = (href: string) =>
    href === "/app" ? pathname === "/app" : pathname?.startsWith(href);

  return (
    <nav
      aria-label="Primary"
      className="absolute inset-x-0 bottom-0 h-20 bg-paper/95 backdrop-blur border-t hairline"
    >
      <div className="relative h-full grid grid-cols-5 items-end pb-3">
        {tabs.slice(0, 2).map((t) => (
          <TabLink key={t.href} {...t} active={!!isActive(t.href)} />
        ))}
        <div className="flex justify-center items-start">
          <Link
            href="/app/score-a-look"
            aria-label="Score a look"
            className="-mt-8 h-16 w-16 rounded-full bg-ink text-white flex items-center justify-center shadow-[0_6px_18px_rgba(20,18,16,0.28)] hover:bg-black active:bg-black"
          >
            <Camera size={26} />
          </Link>
        </div>
        {tabs.slice(2).map((t) => (
          <TabLink key={t.href} {...t} active={!!isActive(t.href)} />
        ))}
      </div>
    </nav>
  );
}

function TabLink({
  href,
  label,
  active,
}: {
  href: string;
  label: string;
  active: boolean;
}) {
  return (
    <Link
      href={href}
      className={clsx(
        "flex flex-col items-center justify-end gap-1 text-[0.62rem] uppercase tracking-[0.22em] font-medium",
        active ? "text-ink" : "text-muted",
      )}
    >
      <span>{label}</span>
      <span
        className={clsx(
          "h-[2px] w-6 rounded-full",
          active ? "bg-bronze" : "bg-transparent",
        )}
      />
    </Link>
  );
}
