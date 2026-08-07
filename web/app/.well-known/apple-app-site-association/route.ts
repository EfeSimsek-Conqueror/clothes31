import { NextResponse } from "next/server";

export const dynamic = "force-static";

const APPLE_TEAM_ID = "NCYFB69WBK";
const BUNDLE_ID = "com.fitrater.app";
const APP_ID = `${APPLE_TEAM_ID}.${BUNDLE_ID}`;

export function GET() {
  return NextResponse.json(
    {
      applinks: {
        apps: [],
        details: [
          {
            appID: APP_ID,
            appIDs: [APP_ID],
            paths: ["*"],
            components: [{ "/": "*" }],
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
