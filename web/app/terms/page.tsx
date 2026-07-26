import type { Metadata } from "next";
import { LegalShell, LegalSection } from "@/components/legal";

export const metadata: Metadata = {
  title: "Terms of Service — Fitrater",
  description: "The terms that govern your use of Fitrater.",
};

const COMPANY = "Cloudgeng";
const CONTACT = "efe@cloudgeng.com";

export default function TermsPage() {
  return (
    <LegalShell title="Terms of Service" updated="26 July 2026">
      <LegalSection heading="1. Agreement">
        <p>
          These terms are an agreement between you and {COMPANY}
          (&ldquo;Fitrater&rdquo;, &ldquo;we&rdquo;), the operator of the
          Fitrater apps and the fitrater.ai website. By creating an account or
          using the service you accept these terms. You must be at least 16
          years old to use Fitrater.
        </p>
      </LegalSection>

      <LegalSection heading="2. The service">
        <p>
          Fitrater lets you photograph outfits and garments and receive
          AI-generated feedback: scores, roasts, comparisons, virtual try-ons,
          look breakdowns, and generated garment images. Results are produced
          by automated models for style guidance and entertainment. They may be
          inaccurate, inconsistent, or unflattering, and they are not
          professional advice of any kind.
        </p>
      </LegalSection>

      <LegalSection heading="3. Your account">
        <p>
          You are responsible for the activity on your account and for keeping
          access to your email and Google account secure. You can delete your
          account at any time in the app (You &rarr; Help &amp; Privacy &rarr;
          Delete my account).
        </p>
      </LegalSection>

      <LegalSection heading="4. Credits and subscriptions">
        <p>
          Features are paid for with in-app credits. Credits are granted on
          sign-up, earned through in-app rewards, included with subscriptions,
          or purchased as one-time packs. Purchases are processed by the app
          store you use (Google Play or the App Store), and refunds are
          governed by that store&rsquo;s policies. Subscriptions renew
          automatically until cancelled in your store account settings; usage
          may be subject to fair-use caps shown in the app.
        </p>
        <p>
          Credits have no monetary value, are non-transferable, and expire when
          your account is deleted. Obtaining credits other than through the
          app — for example by manipulating the API — is prohibited and may
          lead to termination.
        </p>
      </LegalSection>

      <LegalSection heading="5. Your content">
        <p>
          You keep all rights to the photos you submit. You grant us the
          licence needed to store and process them — including sharing them
          with our AI processing providers — solely to provide the features you
          request. Only submit photos you have the right to use; do not submit
          photos of other people without their permission, and do not submit
          unlawful or sexually explicit content.
        </p>
      </LegalSection>

      <LegalSection heading="6. Acceptable use">
        <p>You agree not to:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>
            reverse-engineer, scrape, or access the service other than through
            the apps and website we provide;
          </li>
          <li>
            interfere with the service, other users&rsquo; accounts, or the
            credit system;
          </li>
          <li>use the service to harass others or to break the law.</li>
        </ul>
        <p>
          We may suspend or terminate accounts that violate these terms.
        </p>
      </LegalSection>

      <LegalSection heading="7. Intellectual property">
        <p>
          The Fitrater apps, website, branding, and design are our property or
          that of our licensors. These terms do not grant you any rights in
          them except the right to use the service.
        </p>
      </LegalSection>

      <LegalSection heading="8. Disclaimers and liability">
        <p>
          The service is provided &ldquo;as is&rdquo;. To the extent permitted
          by law, we exclude all warranties and our total liability for any
          claim arising out of the service is limited to the amount you paid us
          in the twelve months before the claim. Nothing in these terms limits
          liability that cannot be limited under applicable law, including
          liability arising from intent or gross negligence.
        </p>
      </LegalSection>

      <LegalSection heading="9. Changes and termination">
        <p>
          We may change or discontinue features, and we may update these terms.
          Material changes will be announced in the app or by email; continuing
          to use the service after a change takes effect means you accept the
          updated terms. You can stop using the service and delete your account
          at any time.
        </p>
      </LegalSection>

      <LegalSection heading="10. Governing law and contact">
        <p>
          These terms are governed by Dutch law, and disputes are subject to
          the competent courts of the Netherlands. Mandatory consumer
          protections of your country of residence remain unaffected.
        </p>
        <p>
          Questions? Contact{" "}
          <a className="underline" href={`mailto:${CONTACT}`}>{CONTACT}</a>.
        </p>
      </LegalSection>
    </LegalShell>
  );
}
