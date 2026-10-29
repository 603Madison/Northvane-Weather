#!/usr/bin/env bash
# Creates a release signing key and prints the values to store as GitHub secrets.
# Keep the .jks file somewhere safe and backed up: if you lose it you can never
# publish an update that installs over existing copies of the app.
set -euo pipefail

OUT="${1:-northvane-release.jks}"
ALIAS="${2:-northvane}"
[ -e "$OUT" ] && { echo "$OUT already exists; refusing to overwrite." >&2; exit 1; }

read -r -s -p "Choose a keystore password: " PASS; echo
read -r -s -p "Repeat it: " PASS2; echo
[ "$PASS" = "$PASS2" ] || { echo "Passwords differ." >&2; exit 1; }

keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Northvane"

cat <<MSG

Created $OUT. Add these repository secrets (Settings > Secrets and variables > Actions):

  RELEASE_KEYSTORE_BASE64   $(base64 -w0 "$OUT" 2>/dev/null | cut -c1-24)...  (run: base64 -w0 $OUT)
  RELEASE_KEYSTORE_PASSWORD (the password you just chose)
  RELEASE_KEY_ALIAS         $ALIAS
  RELEASE_KEY_PASSWORD      (same password)

Never commit the .jks file or keystore.properties (both are git-ignored).
MSG
