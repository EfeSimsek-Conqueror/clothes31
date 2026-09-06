import { NextResponse } from "next/server";

export const dynamic = "force-static";

const PACKAGE_NAME = "com.fitrater.app";

/**
 * Android App Links verification file (Digital Asset Links).
 *
 * These fingerprints are not secrets — they are produced specifically to be
 * published (same as the Apple Team ID hardcoded in the AASA route). They come
 * from Play Console -> Test and release -> App integrity:
 *
 *   1. "App signing key certificate" -> SHA-256 certificate fingerprint.
 *      REQUIRED: this is the key Google Play re-signs the shipped app with, so
 *      it is the one every store install presents.
 *   2. "Upload key certificate" -> SHA-256 certificate fingerprint.
 *      Optional, but keeps locally built / internal-test APKs verifying too.
 *
 * If the signing key ever changes (key rotation, or a new Play app), copy the
 * new fingerprints from that same screen and update the constants below, then
 * redeploy — a stale value silently breaks App Links verification for every
 * installed user. Verify with:
 *   curl -s https://fitrater.ai/.well-known/assetlinks.json
 *
 * For one-off overrides (previews, a second keystore) set ANDROID_CERT_SHA256;
 * when defined it REPLACES the constants below and accepts a comma-separated
 * list.
 */
const PLAY_APP_SIGNING_SHA256 =
  "CD:5C:E8:D5:88:07:BC:07:46:BC:76:70:7A:6B:69:F2:0F:25:7B:36:7E:D0:04:DC:C9:BC:B3:D4:24:4E:ED:E9";
const UPLOAD_KEY_SHA256 =
  "91:8D:32:D1:24:9E:79:0F:8D:EC:11:F2:E6:69:0C:D6:7F:87:8D:DC:92:EA:9E:2E:77:28:05:4A:53:C0:C0:9C";

const envFingerprints = (process.env.ANDROID_CERT_SHA256 ?? "")
  .split(",")
  .map((fp) => fp.trim())
  .filter(Boolean);

const fingerprints =
  envFingerprints.length > 0
    ? envFingerprints
    : [PLAY_APP_SIGNING_SHA256, UPLOAD_KEY_SHA256];

export function GET() {
  return NextResponse.json(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: PACKAGE_NAME,
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ],
    {
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "public, max-age=3600",
      },
    },
  );
}
