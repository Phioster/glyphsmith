#!/usr/bin/env bash
# Signs a CI-built release APK with the real key and, optionally, publishes it.
#
# The signing key lives only on the maintainer's device, in ~/.glyphsmith-release/.
# It is not in the repository and not in GitHub Actions secrets: a key that exists
# in two places can leak from two places. CI therefore builds the release APK
# debug-signed, and this script replaces the signature here.
#
#   tools/sign-release.sh app-release.apk            # sign only
#   tools/sign-release.sh app-release.apk v1.2.0     # sign and publish that tag
#
# Needs: pkg install apksigner
set -euo pipefail

KEYDIR="${GLYPHSMITH_KEYDIR:-$HOME/.glyphsmith-release}"
KEYSTORE="$KEYDIR/glyphsmith-release.jks"
ALIAS="${GLYPHSMITH_ALIAS:-glyphsmith}"

APK="${1:?usage: tools/sign-release.sh <apk> [tag]}"
TAG="${2:-}"

for f in "$KEYSTORE" "$KEYDIR/.storepass" "$KEYDIR/.keypass"; do
	[ -f "$f" ] || { echo "missing: $f"; exit 1; }
done
command -v apksigner >/dev/null || { echo "apksigner missing — pkg install apksigner"; exit 1; }

STOREPASS="$(cat "$KEYDIR/.storepass")"
KEYPASS="$(cat "$KEYDIR/.keypass")"
OUT="${APK%.apk}-signed.apk"

apksigner sign \
	--ks "$KEYSTORE" --ks-key-alias "$ALIAS" \
	--ks-pass "pass:$STOREPASS" --key-pass "pass:$KEYPASS" \
	--v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
	--out "$OUT" "$APK"

# Proof rather than trust: an APK still carrying the debug key would install and
# run perfectly, and the only way to notice is to look.
CERTS="$(apksigner verify --print-certs "$OUT")"
echo "$CERTS" | grep -i "Signer .* certificate DN" || true
if echo "$CERTS" | grep -q "CN=Android Debug"; then
	echo "ERROR: still debug-signed"; exit 1
fi
if ! echo "$CERTS" | grep -q "CN=Glyphsmith"; then
	echo "ERROR: unexpected signer — release key not used"; exit 1
fi
echo "signed with the release key: $OUT"

if [ -n "$TAG" ]; then
	VERSION="${TAG#v}"
	FINAL="$(dirname "$OUT")/glyphsmith-${VERSION}.apk"
	mv "$OUT" "$FINAL"
	gh release view "$TAG" >/dev/null 2>&1 || gh release create "$TAG" --generate-notes
	gh release upload "$TAG" "$FINAL" --clobber
	echo "published to release $TAG"
fi
