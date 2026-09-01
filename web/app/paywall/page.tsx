import Link from "next/link";
import {
  Eyebrow,
  PhoneFrame,
  PhoneScroll,
  PrimaryLink,
  SerifDisplay,
} from "@/components/design-system";

const features = [
  { n: "I.", text: "Unlimited scoring, with Hem's per-piece notes." },
  { n: "II.", text: "Try on — wear any piece on your own photo." },
  { n: "III.", text: "Studio without the cap & the Sunday letter." },
];

export default function PaywallPage() {
  return (
    <PhoneFrame>
      <PhoneScroll padBottomNav={false}>
        <div className="px-8 pt-14 pb-12">
          <Eyebrow>Fitrater Pro</Eyebrow>
          <SerifDisplay size="hero" className="mt-3">
            Every feature.
            <br />
            Nothing held back.
          </SerifDisplay>

          <ul className="mt-10 space-y-5 border-t hairline pt-6">
            {features.map((f) => (
              <li key={f.n} className="flex gap-5">
                <span className="serif text-bronze text-lg leading-none w-8">
                  {f.n}
                </span>
                <span className="serif text-[19px] leading-snug text-ink">
                  {f.text}
                </span>
              </li>
            ))}
          </ul>

          {/* Annual — featured */}
          <div className="mt-10 relative bg-card border border-ink rounded-[6px] p-6">
            <span className="absolute -top-3 left-6 bg-bronze text-white text-[0.6rem] tracking-[0.22em] uppercase px-2 h-6 inline-flex items-center rounded-sm">
              Save 55%
            </span>
            <div className="flex items-baseline justify-between">
              <span className="serif text-xl">Annual</span>
              <span className="serif text-2xl">$79.99</span>
            </div>
            <p className="text-muted mt-2 text-sm">
              7 days free, then $79.99 / year.
            </p>
          </div>

          {/* Monthly */}
          <div className="mt-4 border hairline rounded-[6px] p-6 flex items-baseline justify-between">
            <span className="serif text-xl">Monthly</span>
            <span className="serif text-lg">$14.99 / month</span>
          </div>

          <div className="mt-10 space-y-3">
            <PrimaryLink href="/app" full>
              Start 7-Day Free Trial
            </PrimaryLink>
            <Link
              href="/support"
              className="block w-full text-center text-sm text-muted underline underline-offset-4"
            >
              Restore purchases &amp; billing help
            </Link>
          </div>

          <p className="mt-6 text-[0.72rem] leading-relaxed text-muted">
            Monthly is $14.99 / month after a 3-day free trial. Both plans
            renew automatically until cancelled in your App Store or Google
            Play account settings. Prices in USD; your store charges in your
            local currency.
          </p>
        </div>
      </PhoneScroll>
    </PhoneFrame>
  );
}
