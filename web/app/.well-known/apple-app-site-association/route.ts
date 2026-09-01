import { NextResponse } from "next/server";

export const dynamic = "force-static";

const APPLE_TEAM_ID = "NCYFB69WBK";
const BUNDLE_ID = "com.fitrater.app";
const APP_ID = `${APPLE_TEAM_ID}.${BUNDLE_ID}`;

export function GET() {
  return NextResponse.json(
    {
      // Universal links are scoped to the auth callback and nothing else. The
      // iOS app only knows how to consume a Supabase auth callback (see
      // `FitraterApp.isAuthCallback`); claiming "*" meant every marketing and
      // legal page — /privacy, /terms, /support — opened the app instead of
      // the web page, which is a dead end for anyone who is not signing in.
      applinks: {
        apps: [],
        details: [
          {
            appID: APP_ID,
            appIDs: [APP_ID],
            paths: ["/auth/callback", "/auth/callback/*"],
            components: [
              { "/": "/auth/callback" },
              { "/": "/auth/callback/*" },
            ],
          },
        ],
      },
      webcredentials: {
        apps: [APP_ID],
      },
    },
    {
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "public, max-age=3600",
      },
    },
  );
}
