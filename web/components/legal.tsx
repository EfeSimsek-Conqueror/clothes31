import Link from "next/link";

export function LegalShell({
  title,
  updated,
  children,
}: {
  title: string;
  updated: string;
  children: React.ReactNode;
}) {
  return (
    <main className="relative min-h-dvh bg-paper text-ink">
      <div className="paper-grain absolute inset-0 pointer-events-none" />

      <header className="relative z-10 mx-auto max-w-3xl px-6 pt-6 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2">
          <span className="serif text-xl">FitScore</span>
          <span className="text-[0.6rem] uppercase tracking-[0.24em] text-bronze">
            / Hem
          </span>
        </Link>
        <nav className="flex items-center gap-6 text-sm text-muted">
          <Link href="/privacy" className="hover:text-ink">Privacy</Link>
          <Link href="/terms" className="hover:text-ink">Terms</Link>
        </nav>
      </header>

      <article className="relative z-10 mx-auto max-w-3xl px-6 pt-16 pb-24">
        <h1 className="serif text-4xl md:text-5xl mb-3">{title}</h1>
        <p className="text-sm text-muted mb-12">Last updated: {updated}</p>
        <div className="space-y-10 leading-relaxed text-[0.95rem]">
          {children}
        </div>
      </article>
    </main>
  );
}

export function LegalSection({
  heading,
  children,
}: {
  heading: string;
  children: React.ReactNode;
}) {
  return (
    <section>
      <h2 className="serif text-2xl mb-4">{heading}</h2>
      <div className="space-y-4 text-ink/85">{children}</div>
    </section>
  );
}
