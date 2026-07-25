import type { ReactNode } from "react";
import { BottomTabBar, PhoneFrame } from "@/components/design-system";

export default function AppShellLayout({ children }: { children: ReactNode }) {
  return (
    <PhoneFrame>
      <div className="relative flex-1 flex flex-col overflow-hidden">
        {children}
        <BottomTabBar />
      </div>
    </PhoneFrame>
  );
}
