import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import {
  Eyebrow,
  PhoneScroll,
  SectionPill,
  SectionRow,
  SerifDisplay,
} from "@/components/design-system";

const scoring = [
  { title: "Outfit score", subtitle: "One shot, one number.", pill: "1 photo" },
  { title: "Piece-by-piece", subtitle: "Read each item cleanly.", pill: "1 photo" },
  { title: "Deep sub-scores", subtitle: "Fit, colour, proportion.", pill: "1 photo" },
  { title: "Occasion score", subtitle: "Right for the room.", pill: "1 photo" },
  { title: "Swap suggestions", subtitle: "One trade, better read.", pill: "1 photo" },
  { title: "Before / after", subtitle: "Compare the change.", pill: "2 photos" },
];

const mirror = [
  { title: "Virtual try-on", subtitle: "Any piece on your photo.", pill: "2 photos" },
  { title: "Digital closet", subtitle: "Auto-tagged and searchable.", pill: "No camera" },
  { title: "Outfit builder", subtitle: "Compose from your closet.", pill: "No camera" },
  { title: "Weather outfit", subtitle: "Based on tomorrow's high.", pill: "No camera" },
];

export default function ScoreALookPage() {
  return (
    <PhoneScroll>
      <div className="px-6 pt-8 pb-4 flex items-center justify-between">
        <Link
          href="/app"
          aria-label="Back"
          className="h-9 w-9 -ml-2 flex items-center justify-center text-ink"
        >
          <ChevronLeft />
        </Link>
        <Eyebrow>Score a look</Eyebrow>
        <span className="w-9" />
      </div>

      <div className="px-6">
        <SerifDisplay size="xl" className="mt-2">
          What are we
          <br />
          reading today?
        </SerifDisplay>
      </div>

      <div className="px-6 mt-8">
        <Eyebrow>Scoring</Eyebrow>
        <ul className="mt-2">
          {scoring.map((r) => (
            <SectionRow
              key={r.title}
              title={r.title}
              subtitle={r.subtitle}
              right={
                <div className="flex items-center gap-3">
                  <SectionPill>{r.pill}</SectionPill>
                  <ChevronRight size={18} className="text-muted" />
                </div>
              }
            />
          ))}
        </ul>
      </div>

      <div className="px-6 mt-8">
        <Eyebrow>Mirror & closet</Eyebrow>
        <ul className="mt-2">
          {mirror.map((r) => (
            <SectionRow
              key={r.title}
              title={r.title}
              subtitle={r.subtitle}
              right={
                <div className="flex items-center gap-3">
                  <SectionPill tone={r.pill === "No camera" ? "muted" : "bronze"}>
                    {r.pill}
                  </SectionPill>
                  <ChevronRight size={18} className="text-muted" />
                </div>
              }
            />
          ))}
        </ul>
      </div>
    </PhoneScroll>
  );
}
