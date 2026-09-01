#!/usr/bin/env bash
# Fitrater — App Store submission script
# Archives the iOS app, exports .ipa, and uploads to App Store Connect via altool.
#
# REQUIREMENTS BEFORE RUNNING:
#   1. Apple Developer Program membership active
#   2. App Store Connect API key (.p8 file) at ~/.appstoreconnect/private_keys/
#   3. App record already created in App Store Connect with bundle id com.fitrater.app
#   4. Fill in the three variables below (KEY_ID, ISSUER_ID)

set -euo pipefail

# ============================================================================
# CONFIGURE THESE
# ============================================================================
# From ASC → Users and Access → Integrations → App Store Connect API
# Two keys were found on this machine:
#   ~/.appstoreconnect/private_keys/AuthKey_B77442T5FR.p8   → KEY_ID = B77442T5FR
#   ~/.appstoreconnect/private_keys/AuthKey_BA97HB3JZJ.p8   → KEY_ID = BA97HB3JZJ
# Pick one and put its 10-char key ID here:
KEY_ID="BA97HB3JZJ"

# Issuer ID (UUID) is shown at the top of the same ASC page. Example:
# ISSUER_ID="69a6de7f-XXXX-XXXX-XXXX-5bc39c9d75e3"
ISSUER_ID="dbaebda1-f330-41c9-88e5-1b7c77f68829"

# ============================================================================
# PATHS (edit only if project moves)
# ============================================================================
PROJECT="/Users/efe/clothes31/ios/FitScore/FitScore.xcodeproj"
SCHEME="Fitrater"
CONFIG="Release"
ARCHIVE_PATH="/tmp/Fitrater.xcarchive"
EXPORT_DIR="/tmp/Fitrater-export"
EXPORT_OPTIONS="/Users/efe/clothes31/ios/store/ExportOptions.plist"
IPA_PATH="${EXPORT_DIR}/Fitrater.ipa"

echo "=== [1/4] Archiving ==="
rm -rf "$ARCHIVE_PATH"
xcodebuild \
  -scheme "$SCHEME" \
  -project "$PROJECT" \
  -configuration "$CONFIG" \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  archive

# ============================================================================
# Version assertion — the archive must carry the build number the project says.
# Info.plist once hardcoded CFBundleVersion while GENERATE_INFOPLIST_FILE = NO,
# so every archive silently shipped a stale build number. Never again.
# ============================================================================
echo "=== [2/4] Verifying archived build number ==="
APP_PLIST="${ARCHIVE_PATH}/Products/Applications/${SCHEME}.app/Info.plist"
if [[ ! -f "$APP_PLIST" ]]; then
  echo "ERROR: No Info.plist inside the archive at $APP_PLIST"
  exit 1
fi

EXPECTED_BUILD=$(xcodebuild \
  -scheme "$SCHEME" \
  -project "$PROJECT" \
  -configuration "$CONFIG" \
  -destination 'generic/platform=iOS' \
  -showBuildSettings 2>/dev/null \
  | awk -F' = ' '/ CURRENT_PROJECT_VERSION = /{print $2; exit}')
ARCHIVED_BUILD=$(plutil -extract CFBundleVersion raw -o - "$APP_PLIST")
ARCHIVED_VERSION=$(plutil -extract CFBundleShortVersionString raw -o - "$APP_PLIST")

echo "Project CURRENT_PROJECT_VERSION: ${EXPECTED_BUILD:-<unset>}"
echo "Archived CFBundleVersion:        ${ARCHIVED_BUILD}  (short: ${ARCHIVED_VERSION})"

if [[ -z "$EXPECTED_BUILD" ]]; then
  echo "ERROR: Could not read CURRENT_PROJECT_VERSION from the project."
  exit 1
fi
if [[ "$ARCHIVED_BUILD" != "$EXPECTED_BUILD" ]]; then
  echo "ERROR: Archived CFBundleVersion ($ARCHIVED_BUILD) != CURRENT_PROJECT_VERSION ($EXPECTED_BUILD)."
  echo "       Info.plist is probably hardcoding a literal instead of \$(CURRENT_PROJECT_VERSION)."
  exit 1
fi

echo "=== [3/4] Exporting .ipa ==="
rm -rf "$EXPORT_DIR"
xcodebuild \
  -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -exportPath "$EXPORT_DIR" \
  -allowProvisioningUpdates

# The exported ipa name is derived from the target's product name.
IPA_FILE=$(ls "$EXPORT_DIR"/*.ipa | head -n1)
echo "IPA: $IPA_FILE"

echo "=== [4/4] Uploading to App Store Connect ==="
if [[ "$ISSUER_ID" == "REPLACE_WITH_ISSUER_UUID_FROM_ASC" ]]; then
  echo "ERROR: Set ISSUER_ID at the top of this script first."
  echo "Find it at: https://appstoreconnect.apple.com/access/integrations/api"
  exit 1
fi

xcrun altool --upload-app \
  -f "$IPA_FILE" \
  -t ios \
  --apiKey "$KEY_ID" \
  --apiIssuer "$ISSUER_ID"

echo
echo "=== DONE ==="
echo "Build uploaded. Processing takes 5-30 minutes."
echo "Watch: https://appstoreconnect.apple.com → Fitrater → TestFlight → iOS Builds"
echo
echo "Next: complete the web-only steps (see APP_STORE_SUBMISSION_CHECKLIST.md phases 3-11)."
