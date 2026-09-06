import { clsx } from "clsx";
import type { ReactNode } from "react";

/**
 * Mobile-first mockup. On small screens this is full-bleed.
 * On desktop it renders a centered 420px column framed like a device.
 */
export function PhoneFrame({
  children,
  className,
  showChrome = true,
}: {
  children: ReactNode;
  className?: string;
  showChrome?: boolean;
}) {
  return (
    <div className="relative w-full min-h-dvh flex items-stretch justify-center md:py-10">
      {/* desktop editorial gutters */}
      <div className="hidden md:block absolute inset-0 pointer-events-none">
        <div className="paper-grain absolute inset-0" />
      </div>

      <div
        className={clsx(
          "relative w-full max-w-[420px] min-h-dvh md:min-h-[860px] md:h-[860px] bg-paper md:rounded-[38px] md:border md:border-ink/15 md:shadow-[0_25px_60px_rgba(20,18,16,0.20)] overflow-hidden flex flex-col",
          className,
        )}
      >
        {showChrome ? (
          <div className="hidden md:flex items-center justify-between px-6 pt-3 pb-1 text-[11px] tracking-widest text-ink/70 font-medium">
            <span>9:41</span>
            <span className="h-1.5 w-16 rounded-full bg-ink/20" />
            <span className="uppercase">Fitrater</span>
          </div>
        ) : null}
        {children}
      </div>
    </div>
  );
}

export function PhoneScroll({
  children,
  className,
  padBottomNav = true,
}: {
  children: ReactNode;
  className?: string;
  padBottomNav?: boolean;
}) {
  return (
    <div
      className={clsx(
        "flex-1 overflow-y-auto",
        padBottomNav && "pb-28",
        className,
      )}
    >
      {children}
    </div>
  );
}
