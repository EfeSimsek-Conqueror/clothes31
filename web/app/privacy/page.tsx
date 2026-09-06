import type { Metadata } from "next";
import { LegalShell, LegalSection } from "@/components/legal";

export const metadata: Metadata = {
  title: "Privacy Policy — Fitrater",
  description: "How Fitrater collects, uses, and protects your data.",
};

const COMPANY = "Cloudgeng";
const CONTACT = "efe@cloudgeng.com";

export default function PrivacyPage() {
  return (
    <LegalShell title="Privacy Policy" updated="1 September 2026">
      <LegalSection heading="1. Who we are">
        <p>
          Fitrater (also branded &ldquo;Hem&rdquo;) is operated by{" "}
          {COMPANY}, based in the Netherlands. We are the data controller for
          the personal data described in this policy. You can reach us at{" "}
          <a className="underline" href={`mailto:${CONTACT}`}>{CONTACT}</a>.
        </p>
        <p>
          This policy covers the Fitrater mobile apps and the fitrater.ai
          website.
        </p>
      </LegalSection>

      <LegalSection heading="2. What we collect">
        <p>
          <strong>Account data.</strong> When you sign in we collect your email
          address and, if you use Google sign-in, your name and profile picture
          as provided by Google, along with a unique account identifier.
        </p>
        <p>
          <strong>Photos and content you submit.</strong> Outfit and garment
          photos you capture or upload are stored on our servers and processed
          to provide the features you request (scoring, comparison, virtual
          try-on, decoding a look, and garment generation). The results —
          scores, notes, and generated images — are stored with your account.
        </p>
        <p>
          <strong>Purchases and credits.</strong> When you buy credits or a
          subscription, the purchase is handled by the app store (Google Play
          or the App Store) and our billing provider RevenueCat. We receive
          purchase records — not your payment card details — and keep a ledger
          of the credits you receive and spend.
        </p>
        <p>
          <strong>Approximate location (Android only, optional).</strong> On
          Android, if you grant the permission, the app reads your approximate
          location to show local weather on the home screen. The coordinates go
          to the weather service to fetch that forecast and are not stored by
          us. The iOS app does not request or use your location at all.
        </p>
        <p>
          <strong>Crash diagnostics.</strong> If the app crashes, an automatic
          report is sent to our crash-reporting provider containing the device
          model, operating system version, app version, and the technical
          details of the crash. These reports are not linked to your account
          and are used only to find and fix bugs.
        </p>
        <p>
          <strong>Preferences and support.</strong> We store your in-app
          settings (such as notification preferences) and any messages you send
          to support.
        </p>
        <p>
          We do not show third-party advertising and we do not sell your
          personal data.
        </p>
      </LegalSection>

      <LegalSection heading="3. How we use your data">
        <p>We use your data to:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>
            provide the service — create your account, process your photos to
            generate results, sync your closet and journal, and operate the
            credit system (performance of our contract with you);
          </li>
          <li>
            process purchases and prevent abuse of the credit system
            (performance of contract and legitimate interest);
          </li>
          <li>
            show local weather on Android when you grant location access
            (consent, which you can withdraw at any time in your device
            settings);
          </li>
          <li>
            diagnose crashes so we can fix them (legitimate interest);
          </li>
          <li>
            respond to support requests and keep the service secure (legitimate
            interest);
          </li>
          <li>comply with legal obligations that apply to us.</li>
        </ul>
      </LegalSection>

      <LegalSection heading="4. AI processing">
        <p>
          Fitrater&rsquo;s core features are powered by artificial intelligence.
          Photos you submit are shared with third-party AI processing providers
          solely to generate the result you requested (for example a score,
          comparison, try-on image, or generated garment). Results are
          automated and provided for style guidance and entertainment; they are
          opinions of a model, not facts about you.
        </p>
      </LegalSection>

      <LegalSection heading="5. Who we share data with">
        <p>
          We use a small number of service providers (processors) to run
          Fitrater:
        </p>
        <ul className="list-disc pl-6 space-y-2">
          <li>
            <strong>Supabase</strong> — hosting, database, authentication, and
            photo storage;
          </li>
          <li>
            <strong>AI model providers</strong> — processing of submitted
            photos to generate results;
          </li>
          <li>
            <strong>RevenueCat</strong> — purchase and subscription management;
          </li>
          <li>
            <strong>Google</strong> — sign-in and Google Play billing;
          </li>
          <li>
            <strong>Google Firebase Crashlytics</strong> — crash and error
            diagnostics (device model, OS version, app version, and the crash
            stack trace). We do not use Firebase Analytics, and there is no
            advertising SDK in the app;
          </li>
          <li>
            <strong>Open-Meteo</strong> — weather forecasts. It receives
            coordinates only, and only for that lookup; no device location is
            sent from the iOS app.
          </li>
        </ul>
        <p>
          Some providers process data outside the European Economic Area. Where
          that happens we rely on appropriate safeguards, such as the European
          Commission&rsquo;s Standard Contractual Clauses.
        </p>
      </LegalSection>

      <LegalSection heading="6. Retention and deletion">
        <p>
          We keep your data for as long as your account exists. You can delete
          your account at any time in the app (You &rarr; Help &amp; Privacy
          &rarr; Delete my account) or by emailing{" "}
          <a className="underline" href={`mailto:${CONTACT}`}>{CONTACT}</a>.
          Deleting your account removes your photos, results, closet, journal,
          and credit history. Purchase records may be retained where required
          for tax and accounting law.
        </p>
      </LegalSection>

      <LegalSection heading="7. Your rights">
        <p>
          Under the GDPR you have the right to access, correct, delete, and
          receive a copy of your personal data, to restrict or object to our
          processing, and to withdraw consent at any time. To exercise these
          rights, contact us at{" "}
          <a className="underline" href={`mailto:${CONTACT}`}>{CONTACT}</a>.
        </p>
        <p>
          You also have the right to lodge a complaint with your data
          protection authority. In the Netherlands this is the Autoriteit
          Persoonsgegevens.
        </p>
      </LegalSection>

      <LegalSection heading="8. Children">
        <p>
          Fitrater is not directed at children and is not intended for anyone
          under 16. We do not knowingly collect personal data from children.
        </p>
      </LegalSection>

      <LegalSection heading="9. Security">
        <p>
          Data is encrypted in transit. Access to production data is limited to
          the people and systems that need it to operate the service.
        </p>
      </LegalSection>

      <LegalSection heading="10. Changes">
        <p>
          We may update this policy from time to time. Material changes will be
          announced in the app or by email. The &ldquo;last updated&rdquo; date
          above always reflects the current version.
        </p>
      </LegalSection>
    </LegalShell>
  );
}
