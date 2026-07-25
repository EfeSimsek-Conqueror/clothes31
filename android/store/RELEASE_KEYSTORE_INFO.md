# Fitrater — Release keystore

**Do not commit** `keystore/fitrater-release.jks` or `keystore.properties`. Back up the `.jks` file offline (iCloud, 1Password, physical drive). Losing it means you cannot ship an update to this Play Store listing.

## Location
- Keystore: `/Users/efe/clothes31/android/keystore/fitrater-release.jks`
- Properties: `/Users/efe/clothes31/android/keystore.properties`

## Credentials
- Store password: `F1trater!Release2026`
- Key alias: `fitrater`
- Key password: `F1trater!Release2026`
- Validity: 10,000 days

## Fingerprints (upload signing cert)

```
SHA1  : 2E:FF:62:D0:23:0D:29:5C:F7:DD:60:AE:95:12:B8:D9:F3:CE:2E:BC
SHA256: 91:8D:32:D1:24:9E:79:0F:8D:EC:11:F2:E6:69:0C:D6:7F:87:8D:DC:92:EA:9E:2E:77:28:05:4A:53:C0:C0:9C
```

## What to do with the SHA-1

**Google Cloud Console — OAuth 2.0 Client** (project **CLOTHES**, Android client `com.fitrater.app`):
1. Console → APIs & Services → Credentials
2. Find the Android OAuth client for package `com.fitrater.app`
3. Add `2E:FF:62:D0:23:0D:29:5C:F7:DD:60:AE:95:12:B8:D9:F3:CE:2E:BC` under **SHA-1 certificate fingerprint** (in addition to the existing debug SHA-1)
4. Save

**Firebase (if used):** Add the same SHA-1 under Project settings → Your apps → Android → Add fingerprint.

## Play App Signing note

When you upload the first AAB, Google Play will ask whether you want to opt in to Play App Signing. **Do opt in** — Google will hold the app signing key; the JKS above becomes your *upload* key. If you ever lose the upload key you can request a reset. If you had opted out and lost the app signing key, you'd be dead.

After enrolling, Play Console → **Setup → App integrity** shows both:
- App signing key certificate (Google's)
- Upload key certificate (this JKS)

Add the App signing key SHA-1 (from Play Console) to Google OAuth as well, otherwise Google Sign-In will fail on the Play-installed build.
