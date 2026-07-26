import type { Metadata } from "next";
import { LegalShell, LegalSection } from "@/components/legal";

export const metadata: Metadata = {
  title: "Delete your account — Fitrater",
  description: "How to delete your Fitrater account and associated data.",
};

const CONTACT = "efe@cloudgeng.com";

export default function DeleteAccountPage() {
  return (
    <LegalShell title="Delete your account" updated="27 July 2026">
      <LegalSection heading="Delete in the app (fastest)">
        <ol className="list-decimal pl-6 space-y-2">
          <li>Open the Fitrater app and go to the <strong>You</strong> tab.</li>
          <li>
            Tap <strong>Help &amp; Privacy</strong>, then{" "}
            <strong>Delete my account</strong>.
          </li>
          <li>Confirm. Deletion takes effect immediately.</li>
        </ol>
      </LegalSection>

      <LegalSection heading="Delete by email">
        <p>
          If you can no longer access the app, email{" "}
          <a className="underline" href={`mailto:${CONTACT}`}>{CONTACT}</a>{" "}
          from the address linked to your account with the subject
          &ldquo;Delete my account&rdquo;. We will process the request within
          30 days.
        </p>
      </LegalSection>

      <LegalSection heading="What is deleted">
        <p>
          Deleting your account removes your profile, uploaded and generated
          photos, scores and results, closet, journal, and credit history.
        </p>
        <p>
          Purchase records may be retained where required by tax and
          accounting law. Details are in our{" "}
          <a className="underline" href="/privacy">Privacy Policy</a>.
        </p>
      </LegalSection>
    </LegalShell>
  );
}
