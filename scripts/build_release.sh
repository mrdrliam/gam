#!/bin/bash
# Builds the signed release APK and copies it into the project folder.
#
# First run: asks for a password, creates the signing key (gam-release.jks) and keystore.properties.
# Later runs: reuse both and ask nothing.
#
# Keep a copy of gam-release.jks and its password somewhere safe: an update can only be installed
# over the app if it is signed with the same key.
set -euo pipefail

cd "$(dirname "$0")/.."

STORE="gam-release.jks"
PROPS="keystore.properties"
ALIAS="gam"
SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)"
SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"

die() { echo "ERROR: $*" >&2; exit 1; }

# Printable ASCII only: a properties file is not UTF-8, so e.g. Persian letters would break the build.
valid_password() {
    local LC_ALL=C
    [ "${#1}" -ge 6 ] || { echo "Too short: use at least 6 characters."; return 1; }
    case "$1" in
        *[!\ -~]*) echo "Use English letters, digits and symbols only (check your keyboard layout)."; return 1 ;;
        " "* | *" ") echo "No space at the start or end."; return 1 ;;
    esac
}

ask() {
    IFS= read -r -s -p "$1" REPLY || die "no input"
    echo
}

write_props() {
    # a backslash is an escape character in a properties file
    local escaped=${GAM_PASS//\\/\\\\}
    (
        umask 077
        printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
            "$STORE" "$escaped" "$ALIAS" "$escaped" > "$PROPS"
    )
    echo "Saved $PROPS (readable only by you, ignored by git)."
}

if [ ! -f "$STORE" ]; then
    [ ! -f "$PROPS" ] || die "$PROPS exists but $STORE is missing. Restore the key file from your backup."
    echo "Creating the signing key. Choose a password and keep it somewhere safe."
    while true; do
        ask "New password: "
        GAM_PASS="$REPLY"
        valid_password "$GAM_PASS" || continue
        ask "Repeat password: "
        [ "$REPLY" = "$GAM_PASS" ] && break
        echo "The two passwords differ, try again."
    done
    export GAM_PASS
    keytool -genkeypair -keystore "$STORE" -storetype PKCS12 -alias "$ALIAS" \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Gam" \
        -storepass:env GAM_PASS -keypass:env GAM_PASS >/dev/null
    chmod 600 "$STORE"
    echo "Created $STORE."
    write_props
elif [ ! -f "$PROPS" ]; then
    ask "Password of $STORE: "
    GAM_PASS="$REPLY"
    export GAM_PASS
    keytool -list -keystore "$STORE" -alias "$ALIAS" -storepass:env GAM_PASS >/dev/null 2>&1 || die "wrong password"
    write_props
fi
unset GAM_PASS REPLY

echo "Testing and building..."
./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain -q

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || die "no signed APK was produced"

APKSIGNER="$(ls -d "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[ -n "$APKSIGNER" ] || die "apksigner not found under $SDK_DIR/build-tools"
"$APKSIGNER" verify "$APK" || die "signature check failed"

VERSION="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
OUT="Gam-$VERSION.apk"
cp "$APK" "$OUT"
echo "DONE: $PWD/$OUT"
