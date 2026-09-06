import Link from "next/link";
import {
  Eyebrow,
  PrimaryLink,
  PullQuote,
  SerifDisplay,
} from "@/components/design-system";

export default function LandingPage() {
  return (
    <main className="relative min-h-dvh bg-paper text-ink overflow-hidden">
      <div className="paper-grain absolute inset-0 pointer-events-none" />

      {/* Nav */}
      <header className="relative z-10 mx-auto max-w-6xl px-6 pt-6 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="serif text-xl">Fitrater</span>
          <span className="text-[0.6rem] uppercase tracking-[0.24em] text-bronze">
            / Hem
          </span>
        </div>
        <nav className="hidden md:flex items-center gap-8 text-sm text-muted">
          <Link href="#how" className="hover:text-ink">How it works</Link>
          <Link href="#letters" className="hover:text-ink">The Sunday letter</Link>
          <Link href="/signin" className="hover:text-ink">Sign in</Link>
        </nav>
        <PrimaryLink href="/app" className="hidden md:inline-flex h-11 text-xs">
          Open web app
        </PrimaryLink>
      </header>

      {/* Hero — mirrors the Splash screen */}
      <section className="relative z-10 mx-auto max-w-6xl px-6 pt-20 pb-24 grid md:grid-cols-12 gap-12 items-end">
        <div className="md:col-span-7">
          <Eyebrow className="mb-8 block">Fitrater</Eyebrow>
          <SerifDisplay size="hero" className="mb-6">
            Your closet,
            <br />
            finally honest.
          </SerifDisplay>
          <p className="text-muted text-lg max-w-md leading-relaxed">
            Hem — the taste editor in your pocket — scores every look, keeps
            your closet honest, and writes you a letter every Sunday.
          </p>
          <div className="mt-10 flex flex-col sm:flex-row gap-3 max-w-md">
            <PrimaryLink href="/app" full>
              Get the app
            </PrimaryLink>
            <PrimaryLink href="/app" variant="secondary" full>
              Continue on web
            </PrimaryLink>
          </div>
        </div>

        <aside className="md:col-span-5">
          <figure className="relative rounded-[6px] overflow-hidden aspect-[3/4] bg-card border hairline">
            <div
              className="absolute inset-0"
              style={{
                background:
                  "radial-gradient(120% 80% at 20% 0%, rgba(176,116,58,0.16), transparent 60%), radial-gradient(80% 60% at 100% 100%, rgba(140,96,51,0.14), transparent 60%)",
              }}
            />
            <div className="absolute inset-0 flex flex-col justify-between p-8">
              <Eyebrow>Volume I · Issue 07</Eyebrow>
              <div>
                <PullQuote attribution="— Hem, on Friday">
                  “The trousers do the talking.
                  <br /> Let them.”
                </PullQuote>
              </div>
            </div>
          </figure>
        </aside>
      </section>

      {/* Editorial sections */}
      <section
        id="how"
        className="relative z-10 mx-auto max-w-6xl px-6 py-24 border-t hairline"
      >
        <Eyebrow>How it works</Eyebrow>
        <SerifDisplay size="lg" className="mt-4 max-w-3xl">
          Not another try-on toy. A quiet editor who keeps you sharp.
        </SerifDisplay>

        <div className="mt-16 grid md:grid-cols-3 gap-10 border-t hairline pt-10">
          {[
            {
              n: "I.",
              title: "Photograph the look.",
              body: "One shot in the mirror. Hem reads fit, colour, proportion, and occasion — no filters, no fluff.",
            },
            {
              n: "II.",
              title: "Read the notes.",
              body: "A number, a verdict, and a line of prose. Small suggestions you'll actually act on.",
            },
            {
              n: "III.",
              title: "The Sunday letter arrives.",
              body: "Every week, a page on what you wore, what worked, and what the closet needs next.",
            },
          ].map((c) => (
            <article key={c.n} className="max-w-sm">
              <div className="serif text-bronze text-2xl">{c.n}</div>
              <h3 className="serif text-2xl mt-3">{c.title}</h3>
              <p className="text-muted mt-3 leading-relaxed">{c.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section
        id="letters"
        className="relative z-10 mx-auto max-w-6xl px-6 py-24 border-t hairline grid md:grid-cols-12 gap-12 items-center"
      >
        <div className="md:col-span-6">
          <Eyebrow>The Sunday letter</Eyebrow>
          <SerifDisplay size="lg" className="mt-4">
            An honest page,
            <br />
            every Sunday morning.
          </SerifDisplay>
          <p className="text-muted mt-6 max-w-md leading-relaxed">
            Hem drafts a short letter about the week — where you landed, where
            you slipped, one piece worth adding, one worth retiring. Read it
            with the coffee.
          </p>
        </div>
        <figure className="md:col-span-6 bg-card border hairline rounded-[6px] p-8">
          <Eyebrow>Sunday, 20 July</Eyebrow>
          <PullQuote attribution="— Hem">
            You wore the pleated trouser three times this week. It's earning
            its rent. The camel blazer, however, is quietly asking to be lent
            out. Consider it.
          </PullQuote>
        </figure>
      </section>

      <section className="relative z-10 mx-auto max-w-6xl px-6 py-24 border-t hairline">
        <Eyebrow>Field notes</Eyebrow>
        <div className="mt-8 grid md:grid-cols-3 gap-12">
          {[
            { q: "Finally, a scoring app that doesn't scream.", a: "— beta reader, Milan" },
            { q: "The Sunday letter is the reason I stayed.", a: "— beta reader, Brooklyn" },
            { q: "It cured me of the flannel.", a: "— beta reader, Berlin" },
          ].map((n) => (
            <blockquote key={n.a} className="border-l hairline pl-5">
              <p className="serif italic text-xl leading-snug">{n.q}</p>
              <footer className="text-xs uppercase tracking-[0.22em] text-bronze mt-4">
                {n.a}
              </footer>
            </blockquote>
          ))}
        </div>
      </section>

      <section className="relative z-10 mx-auto max-w-6xl px-6 py-24 border-t hairline text-center">
        <Eyebrow>Available now</Eyebrow>
        <SerifDisplay size="xl" className="mt-4 max-w-2xl mx-auto">
          Get dressed like someone is paying attention.
        </SerifDisplay>
        <div className="mt-10 flex flex-col sm:flex-row gap-3 max-w-md mx-auto">
          <PrimaryLink href="/signin" full>
            Get the app
          </PrimaryLink>
          <PrimaryLink href="/app" variant="secondary" full>
            Continue on web
          </PrimaryLink>
        </div>
      </section>

      <footer className="relative z-10 mx-auto max-w-6xl px-6 py-10 border-t hairline flex flex-col md:flex-row md:items-center md:justify-between gap-4 text-xs text-muted">
        <div className="serif text-ink">Fitrater · Hem</div>
        <div className="uppercase tracking-[0.22em]">
          © {new Date().getFullYear()} — Printed on paper stock
        </div>
      </footer>
    </main>
  );
}
