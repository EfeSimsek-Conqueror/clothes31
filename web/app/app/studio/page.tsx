import {
  Eyebrow,
  PhoneScroll,
  PieceCard,
  SerifDisplay,
} from "@/components/design-system";
import { fetchClosetItems, fetchCreditsBalance } from "@/lib/data";

const placeholders = [
  { title: "Camel blazer", category: "Outer", tint: "olive" as const },
  { title: "Cream tee", category: "Top", tint: "bone" as const },
  { title: "Pleated trouser", category: "Bottom", tint: "ink" as const },
  { title: "Rust knit", category: "Top", tint: "rust" as const },
  { title: "Leather loafer", category: "Shoe", tint: "sand" as const },
];

const tintForCategory = (c?: string | null) => {
  switch ((c ?? "").toLowerCase()) {
    case "top":
      return "olive" as const;
    case "outer":
      return "ink" as const;
    case "shoe":
      return "sand" as const;
    case "bottom":
      return "rust" as const;
    default:
      return "bone" as const;
  }
};

export default async function StudioPage() {
  const [items, credits] = await Promise.all([
    fetchClosetItems(20),
    fetchCreditsBalance(),
  ]);
  const pieces =
    items.length > 0
      ? items.map((it) => ({
          title: it.name ?? "Untitled",
          category: it.category ?? "Piece",
          tint: tintForCategory(it.category),
          imageUrl: it.image_url ?? undefined,
        }))
      : placeholders.map((p) => ({ ...p, imageUrl: undefined }));

  return (
    <PhoneScroll>
      <div className="px-6 pt-10 pb-6 flex items-center justify-between">
        <div>
          <Eyebrow>Studio</Eyebrow>
          <SerifDisplay size="xl" className="mt-2">
            Your pieces.
          </SerifDisplay>
        </div>
        <span className="h-9 px-4 rounded-full bg-ink text-white inline-flex items-center gap-1.5 text-xs tracking-widest">
          <span className="text-bronze">✦</span>
          {credits}
        </span>
      </div>

      <div className="px-6">
        <Eyebrow>Your pieces</Eyebrow>
        <div className="mt-3 scroll-x flex gap-3 overflow-x-auto -mx-6 px-6 pb-2">
          {pieces.map((p, i) => (
            <PieceCard key={i} {...p} />
          ))}
        </div>
      </div>

      <div className="px-6 mt-8 grid grid-cols-2 gap-3">
        <button className="rounded-[6px] border hairline bg-card p-5 h-32 text-left flex flex-col justify-between">
          <Eyebrow>+ Create new</Eyebrow>
          <div className="serif text-lg">Build a look</div>
        </button>
        <button className="rounded-[6px] border hairline bg-card p-5 h-32 text-left flex flex-col justify-between">
          <Eyebrow>↑ Import</Eyebrow>
          <div className="serif text-lg">From photos</div>
        </button>
      </div>

      <div className="px-6 mt-8">
        <Eyebrow>Recent outfits</Eyebrow>
        <div className="mt-3 grid grid-cols-3 gap-2">
          {[...Array(6)].map((_, i) => (
            <div
              key={i}
              className="aspect-square rounded-[6px] bg-card border hairline overflow-hidden relative"
            >
              <div
                className="absolute inset-0"
                style={{
                  background: `radial-gradient(80% 60% at ${20 + i * 10}% 20%, rgba(176,116,58,0.18), transparent 65%)`,
                }}
              />
            </div>
          ))}
        </div>
      </div>
    </PhoneScroll>
  );
}
