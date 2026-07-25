import Link from "next/link";
import {
  Eyebrow,
  PhoneScroll,
  PullQuote,
  ScoreChip,
  SerifDisplay,
} from "@/components/design-system";
import { fetchLatestOutfit } from "@/lib/data";
import { scoreLook } from "@/lib/hem";

function formatDate(d = new Date()) {
  const weekday = d.toLocaleString("en-US", { weekday: "long" }).toUpperCase();
  const day = d.getDate();
  const month = d.toLocaleString("en-US", { month: "long" }).toUpperCase();
  return `${weekday} · ${day} ${month}`;
}

export default async function TodayPage() {
  const outfit = await fetchLatestOutfit();
  const s = scoreLook(outfit?.id ?? "today");
  const score = outfit?.score ?? s.overall;
  const image = outfit?.image_url ?? null;

  return (
    <PhoneScroll>
      <div className="px-6 pt-10 pb-8">
        <div className="flex items-start justify-between">
          <div>
            <Eyebrow>{formatDate()}</Eyebrow>
            <SerifDisplay size="xl" className="mt-2">
              Evening, Efe.
            </SerifDisplay>
          </div>
          <div className="flex items-center gap-2 pt-1">
            <span className="text-[11px] text-muted tracking-widest">72°</span>
            <div className="h-9 w-9 rounded-full bg-card border hairline serif flex items-center justify-center text-sm">
              E
            </div>
          </div>
        </div>
      </div>

      {/* Hem this morning */}
      <div className="mx-6 mb-6 bg-card border hairline rounded-[6px] p-5">
        <div className="flex items-center justify-between">
          <Eyebrow>Hem · This morning</Eyebrow>
          <Link
            href="/app/score-a-look"
            className="text-[11px] uppercase tracking-[0.2em] text-ink"
          >
            Try it on →
          </Link>
        </div>
        <PullQuote className="mt-3" attribution="— Hem">
          A cream shirt would settle Friday. The navy trouser is doing its job.
        </PullQuote>
      </div>

      {/* Today's fit */}
      <div className="mx-6">
        <Eyebrow>Today&apos;s fit</Eyebrow>
        <div className="mt-3 relative rounded-[6px] overflow-hidden aspect-[3/4] bg-card border hairline">
          {image ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={image}
              alt="Today's outfit"
              className="absolute inset-0 w-full h-full object-cover"
            />
          ) : (
            <div
              className="absolute inset-0"
              style={{
                background:
                  "radial-gradient(120% 80% at 20% 0%, rgba(176,116,58,0.20), transparent 60%), radial-gradient(80% 60% at 100% 100%, rgba(20,18,16,0.10), transparent 60%)",
              }}
            />
          )}
          <div className="absolute top-3 right-3">
            <ScoreChip score={Number(score)} size="lg" />
          </div>
          <div className="absolute bottom-0 left-0 right-0 p-5 bg-gradient-to-t from-black/40 to-transparent">
            <p className="text-white/90 text-xs uppercase tracking-[0.2em]">
              0.6 above your average · Daily
            </p>
          </div>
        </div>

        <PullQuote className="mt-5" attribution="— Hem">
          {s.quote}
        </PullQuote>

        <Link
          href="/app/score-a-look"
          className="mt-5 inline-flex items-center justify-center w-full h-14 rounded-[4px] bg-ink text-white text-sm uppercase tracking-[0.14em] font-medium"
        >
          ✦ Score a look
        </Link>

        <div className="mt-4 grid grid-cols-2 gap-3">
          <Link
            href="/app/score-a-look"
            className="rounded-[6px] border hairline bg-card p-4 flex flex-col justify-between h-28"
          >
            <Eyebrow>The Mirror</Eyebrow>
            <div className="serif text-lg">Try on a piece</div>
          </Link>
          <Link
            href="/app/score-a-look"
            className="rounded-[6px] border hairline bg-card p-4 flex flex-col justify-between h-28"
          >
            <Eyebrow>Ask Hem</Eyebrow>
            <div className="serif text-lg">What should I wear?</div>
          </Link>
        </div>
      </div>
    </PhoneScroll>
  );
}
