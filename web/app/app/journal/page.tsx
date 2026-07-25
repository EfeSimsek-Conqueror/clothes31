import Link from "next/link";
import {
  Eyebrow,
  PhoneScroll,
  PullQuote,
  SectionRow,
  SerifDisplay,
} from "@/components/design-system";
import { fetchLatestSundayLetter, fetchOutfits } from "@/lib/data";
import { scoreLook } from "@/lib/hem";

function weekday(iso: string) {
  return new Date(iso).toLocaleString("en-US", { weekday: "long" });
}

export default async function JournalPage() {
  const [letter, outfits] = await Promise.all([
    fetchLatestSundayLetter(),
    fetchOutfits(20),
  ]);

  const list =
    outfits.length > 0
      ? outfits.map((o) => ({
          id: o.id,
          title: o.name ?? "Untitled look",
          day: weekday(o.created_at),
          score: o.score ?? scoreLook(o.id).overall,
          swatch: (o.verdict ?? "").slice(0, 1) || "H",
          image: o.image_url,
        }))
      : [
          { id: "a", title: "Cream shirt, olive trouser", day: "Thursday", score: 8.2, swatch: "C", image: null },
          { id: "b", title: "The rust knit", day: "Wednesday", score: 7.4, swatch: "R", image: null },
          { id: "c", title: "Navy on navy", day: "Tuesday", score: 8.9, swatch: "N", image: null },
          { id: "d", title: "The gym day", day: "Monday", score: 6.1, swatch: "G", image: null },
        ];

  return (
    <PhoneScroll>
      <div className="px-6 pt-10 pb-6">
        <Eyebrow>Journal</Eyebrow>
        <SerifDisplay size="xl" className="mt-2">
          Journal.
        </SerifDisplay>
      </div>

      {/* Sunday letter */}
      <div className="mx-6 mb-8 bg-card border border-ink/20 rounded-[6px] p-6">
        <div className="flex items-center justify-between">
          <Eyebrow>The Sunday letter</Eyebrow>
          <span className="text-[11px] text-muted tracking-widest">
            Week of 11–18 Jul
          </span>
        </div>
        <PullQuote className="mt-3" attribution="— Hem">
          {letter?.body ??
            "You wore the pleated trouser three times this week. It's earning its rent. The camel blazer, however, is quietly asking to be lent out."}
        </PullQuote>
      </div>

      <div className="mx-6">
        <Eyebrow>Recent</Eyebrow>
        <ul className="mt-2">
          {list.map((row) => (
            <SectionRow
              key={row.id}
              leading={
                row.image ? (
                  <div className="h-12 w-12 rounded-[4px] overflow-hidden bg-card border hairline">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={row.image}
                      alt=""
                      className="w-full h-full object-cover"
                    />
                  </div>
                ) : (
                  <div className="h-12 w-12 rounded-[4px] bg-[#8A4A2E] text-white/95 serif flex items-center justify-center">
                    {row.swatch}
                  </div>
                )
              }
              title={
                <Link href={`/app/score/${row.id}`}>{row.title}</Link>
              }
              subtitle={row.day}
              right={
                <span className="serif text-bronze text-lg">
                  {Number(row.score).toFixed(1)}
                </span>
              }
            />
          ))}
        </ul>
      </div>
    </PhoneScroll>
  );
}
