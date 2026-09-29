#!/usr/bin/env bash
# Creates the release signing key for Alternate and, optionally, stores it as GitHub Actions secrets.
#
# Usage: scripts/create-release-key.sh [--out FILE] [--alias NAME] [--set-secrets] [--repo OWNER/REPO]
#
#   --out FILE      where to write the keystore (default: ~/alternate-release/alternate-release.jks).
#                   Must be outside this repository.
#   --alias NAME    key alias (default: alternate)
#   --set-secrets   also set KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD with `gh secret set`
#   --repo R        repository for --set-secrets (default: the repository gh resolves for this checkout)
#
# The password is read from the terminal and is never printed or written to disk. The keystore is PKCS12,
# which uses one password for the store and the key, so KEYSTORE_PASSWORD and KEY_PASSWORD get the same value.
#
# Back up the keystore file and the password somewhere safe (a password manager, an offline copy).
# If you lose either, no future release can be installed as an update: everyone has to uninstall and reinstall.
set -euo pipefail

out="$HOME/alternate-release/alternate-release.jks"
alias_name="alternate"
set_secrets=false
repo=""

while [ $# -gt 0 ]; do
  case "$1" in
    --out) out="$2"; shift 2 ;;
    --alias) alias_name="$2"; shift 2 ;;
    --set-secrets) set_secrets=true; shift ;;
    --repo) repo="$2"; shift 2 ;;
    -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
done

command -v keytool >/dev/null || { echo "keytool not found: install a JDK (17 or newer)." >&2; exit 1; }
if $set_secrets; then
  command -v gh >/dev/null || { echo "gh not found: install the GitHub CLI or drop --set-secrets." >&2; exit 1; }
fi

if [ -e "$out" ]; then
  echo "$out already exists. Refusing to overwrite a keystore: releases signed with it could no longer be updated." >&2
  exit 1
fi

out_dir=$(dirname "$out")
mkdir -p "$out_dir"
out_dir=$(cd "$out_dir" && pwd -P)
out="$out_dir/$(basename "$out")"

# Never create the key inside this repository, where it could be committed by accident.
repo_root=$(cd "$(dirname "$0")/.." && pwd -P)
case "$out_dir/" in
  "$repo_root"/*) echo "Refusing to write the keystore inside the repository ($repo_root). Pick a path outside it." >&2; exit 1 ;;
esac

read -r -p "Name for the certificate [Alternate]: " cn
cn=${cn:-Alternate}
# keytool's -dname syntax treats these characters specially.
cn=$(printf '%s' "$cn" | sed 's/[,+=<>#;"\\]/\\&/g')

while true; do
  read -r -s -p "Keystore password (at least 12 characters): " password; echo
  if [ ${#password} -lt 12 ]; then
    echo "Too short, try again."
    continue
  fi
  read -r -s -p "Repeat the password: " repeat; echo
  [ "$password" = "$repeat" ] && break
  echo "The passwords don't match, try again."
done
unset repeat

umask 077
ALTERNATE_KEY_PASSWORD="$password" keytool -genkeypair \
  -keystore "$out" \
  -storetype PKCS12 \
  -alias "$alias_name" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -dname "CN=$cn" \
  -storepass:env ALTERNATE_KEY_PASSWORD \
  -keypass:env ALTERNATE_KEY_PASSWORD \
  -noprompt

echo
echo "Created $out (alias '$alias_name', RSA 4096, valid for 30 years)."
echo "SHA-256 fingerprint:"
ALTERNATE_KEY_PASSWORD="$password" keytool -list -keystore "$out" -alias "$alias_name" \
  -storepass:env ALTERNATE_KEY_PASSWORD | grep -i 'SHA-256\|SHA256' || true

if $set_secrets; then
  repo_args=()
  [ -n "$repo" ] && repo_args=(--repo "$repo")
  echo
  echo "Setting GitHub Actions secrets..."
  # Values go through stdin so they never appear on a command line.
  base64 < "$out" | tr -d '\n' | gh secret set KEYSTORE_BASE64 ${repo_args[@]+"${repo_args[@]}"}
  printf '%s' "$password" | gh secret set KEYSTORE_PASSWORD ${repo_args[@]+"${repo_args[@]}"}
  printf '%s' "$alias_name" | gh secret set KEY_ALIAS ${repo_args[@]+"${repo_args[@]}"}
  printf '%s' "$password" | gh secret set KEY_PASSWORD ${repo_args[@]+"${repo_args[@]}"}
  echo "Secrets set."
fi
unset password

cat <<EOF

IMPORTANT: back up the keystore and its password now.
  - Copy $out to at least one safe place outside this computer.
  - Store the password (and the alias '$alias_name') in a password manager.
If you lose the keystore or the password, no future release can update the installed app.
Never commit the keystore or put the password in the repository.
EOF

if ! $set_secrets; then
  cat <<EOF

Next: add the GitHub secrets (see ANDROID_RELEASE_SETUP.md). From this keystore:
  base64 < "$out" | tr -d '\n' | gh secret set KEYSTORE_BASE64
  gh secret set KEYSTORE_PASSWORD   # prompts for the value
  gh secret set KEY_ALIAS --body '$alias_name'
  gh secret set KEY_PASSWORD        # same value as KEYSTORE_PASSWORD
EOF
fi
