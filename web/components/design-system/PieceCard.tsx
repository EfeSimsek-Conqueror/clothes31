import { clsx } from "clsx";

const tints: Record<string, string> = {
  olive: "bg-[#7A7A45] text-white/95",
  rust: "bg-[#8A4A2E] text-white/95",
  ink: "bg-[#1B1917] text-white/95",
  sand: "bg-[#D9CBB2] text-ink",
  bone: "bg-[#EFE7D6] text-ink",
};

export function PieceCard({
  title,
  category,
  tint = "olive",
  imageUrl,
  className,
}: {
  title: string;
  category?: string;
  tint?: keyof typeof tints;
  imageUrl?: string;
  className?: string;
}) {
  return (
    <div
      className={clsx(
        "relative flex-none w-40 aspect-[3/4] rounded-[6px] overflow-hidden flex flex-col justify-end p-3",
        tints[tint],
        className,
      )}
    >
      {imageUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={imageUrl}
          alt={title}
          className="absolute inset-0 w-full h-full object-cover opacity-90"
        />
      ) : (
        <div className="absolute inset-0 pointer-events-none">
          <div
            className="absolute inset-0 opacity-[0.18]"
            style={{
              backgroundImage:
                "radial-gradient(rgba(255,255,255,0.4) 1px, transparent 1px)",
              backgroundSize: "4px 4px",
            }}
          />
        </div>
      )}
      <div className="relative z-10">
        {category ? (
          <div className="text-[0.6rem] uppercase tracking-[0.2em] opacity-80 mb-1">
            {category}
          </div>
        ) : null}
        <div className="serif text-base leading-tight">{title}</div>
      </div>
    </div>
  );
}
