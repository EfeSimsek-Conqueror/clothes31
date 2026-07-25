import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import {
  Eyebrow,
  PhoneScroll,
  PullQuote,
  ScoreChip,
  SerifDisplay,
} from "@/components/design-system";
import { fetchOutfitById } from "@/lib/data";
import { scoreLook } from "@/lib/hem";

export default async function ScoreDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const outfit = await fetchOutfitById(id);
  const s = scoreLook(id);
  const score = outfit?.score ?? s.overall;

  return (
    <PhoneScroll>
      <div className="px-6 pt-8 pb-6 flex items-center justify-between">
        <Link href="/app" aria-label="Back" className="h-9 w-9 -ml-2 flex items-center justify-center text-ink">
          <ChevronLeft />
        </Link>
        <Eyebrow>Look #{id.slice(0, 6)}</Eyebrow>
        <span className="w-9" />
      </div>

      <div className="px-6">
        <div className="relative rounded-[6px] overflow-hidden aspect-[3/4] bg-card border hairline">
          {outfit?.image_url ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={outfit.image_url}
              alt=""
              className="absolute inset-0 w-full h-full object-cover"
            />
          ) : (
            <div
              className="absolute inset-0"
              style={{
                background:
                  "radial-gradient(120% 80% at 20% 0%, rgba(176,116,58,0.20), transparent 60%)",
              }}
            />
          )}
          <div className="absolute top-3 right-3">
            <ScoreChip score={Number(score)} size="lg" />
          </div>
        </div>

        <div className="mt-6">
          <SerifDisplay size="lg">{s.verdict}</SerifDisplay>
          <PullQuote className="mt-3" attribution="— Hem">
            {s.quote}
          </PullQuote>
        </div>

        <div className="mt-8">
          <Eyebrow>Subscores</Eyebrow>
          <div className="mt-3 grid grid-cols-2 gap-3">
            {s.subscores.map((sub) => (
              <div
                key={sub.key}
                className="border hairline rounded-[6px] p-4 bg-card"
              >
                <div className="text-[11px] uppercase tracking-[0.2em] text-muted">
                  {sub.label}
                </div>
                <div className="serif text-2xl mt-1">{sub.value.toFixed(1)}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-8 mb-4">
          <Eyebrow>Pieces</Eyebrow>
          <ul className="mt-2 divide-y hairline border-y hairline">
            {s.pieces.map((p) => (
              <li key={p.label} className="py-4 flex items-center gap-4">
                <div className="flex-1">
                  <div className="serif text-lg">{p.label}</div>
                  <div className="text-sm text-muted">{p.note}</div>
                </div>
                <ScoreChip score={p.score} size="sm" />
              </li>
            ))}
          </ul>
        </div>
      </div>
    </PhoneScroll>
  );
}
