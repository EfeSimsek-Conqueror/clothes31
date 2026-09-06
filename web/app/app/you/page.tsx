import {
  Eyebrow,
  PhoneScroll,
  SectionRow,
  SerifDisplay,
} from "@/components/design-system";
import { ChevronRight } from "lucide-react";
import { HonestyDial } from "./honesty-dial";

export default function YouPage() {
  return (
    <PhoneScroll>
      <div className="px-6 pt-10 pb-6 flex items-center gap-4">
        <div className="h-16 w-16 rounded-full bg-card border hairline serif text-2xl flex items-center justify-center">
          E
        </div>
        <div>
          <Eyebrow>Reader</Eyebrow>
          <SerifDisplay size="lg" className="mt-1">
            Efe
          </SerifDisplay>
          <div className="text-sm text-muted">Member since July</div>
        </div>
      </div>

      <div className="mx-6 mb-8 bg-card border hairline rounded-[6px] p-5">
        <Eyebrow>Honesty dial</Eyebrow>
        <p className="text-sm text-muted mt-2">
          Choose how sharply Hem writes.
        </p>
        <HonestyDial />
      </div>

      <div className="mx-6">
        <Eyebrow>Settings</Eyebrow>
        <ul className="mt-2">
          {[
            { title: "Subscription", subtitle: "Fitrater Pro · Annual" },
            { title: "Notifications", subtitle: "Weekly Sunday letter" },
            { title: "Privacy", subtitle: "Photos stay on your device" },
            { title: "Sign out", subtitle: "" },
          ].map((row) => (
            <SectionRow
              key={row.title}
              title={row.title}
              subtitle={row.subtitle || undefined}
              right={<ChevronRight size={18} className="text-muted" />}
            />
          ))}
        </ul>
      </div>
    </PhoneScroll>
  );
}
