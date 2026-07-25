import { Eyebrow, PhoneFrame, PrimaryLink, SerifDisplay } from "@/components/design-system";
import { SigninForm } from "./signin-form";

export default function SigninPage() {
  return (
    <PhoneFrame>
      <div className="flex flex-col h-full px-8 pt-16 pb-10">
        <Eyebrow>Welcome</Eyebrow>
        <SerifDisplay size="hero" className="mt-3">
          Sign in.
        </SerifDisplay>
        <p className="text-muted mt-4 max-w-[280px] leading-relaxed">
          Your closet and journal follow you across devices.
        </p>

        <div className="mt-auto space-y-3">
          <SigninForm />
          <PrimaryLink href="/onboarding" variant="ghost" full className="text-muted">
            Skip for now
          </PrimaryLink>
        </div>
      </div>
    </PhoneFrame>
  );
}
