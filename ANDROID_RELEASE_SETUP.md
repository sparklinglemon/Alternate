# Android Release Setup

Releases of this fork are signed with a release key that belongs to this fork. Every release signed with the
same key installs as an in-place update over the previous one. This document covers creating that key once,
storing it as GitHub secrets, and publishing releases.

## 1. Create the release key (once)

You need a JDK (17 or newer, for `keytool`) and, to set the secrets from the script, the GitHub CLI (`gh`),
logged in with access to this repository.

```bash
scripts/create-release-key.sh --set-secrets
```

The script:

- asks for a certificate name and a password (at least 12 characters, never printed or saved),
- creates an RSA 4096 key valid for 30 years in `~/alternate-release/alternate-release.jks`
  (change with `--out`; it refuses paths inside the repository and never overwrites an existing keystore),
- with `--set-secrets`, stores the four secrets below in the repository with `gh secret set`.

Run `scripts/create-release-key.sh --help` for all options.

> **Back up the keystore and its password.** Copy the `.jks` file to at least one safe place off this
> computer and keep the password and alias in a password manager. If you lose either, no future release can
> be installed as an update: every user would have to uninstall and reinstall. Never commit the keystore, the
> password, or a base64 copy of it (`.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties` and
> `*_base64.txt`, but don't rely on it).

## 2. GitHub secrets

The release workflow needs these repository secrets (**Settings → Secrets and variables → Actions**):

| Secret              | Value                                                    |
| ------------------- | -------------------------------------------------------- |
| `KEYSTORE_BASE64`   | The keystore file, base64 encoded on one line            |
| `KEYSTORE_PASSWORD` | The keystore password                                    |
| `KEY_ALIAS`         | The key alias (`alternate` unless you chose another)     |
| `KEY_PASSWORD`      | The key password (the same as the keystore password)     |

The script sets them with `--set-secrets`. To set them by hand from an existing keystore:

```bash
base64 < ~/alternate-release/alternate-release.jks | tr -d '\n' | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD   # prompts for the value
gh secret set KEY_ALIAS --body alternate
gh secret set KEY_PASSWORD        # same value as KEYSTORE_PASSWORD
```

## 3. Publish a release

1. Bump the version in `android/app/build.gradle`:
   - `versionCode` must be **higher than in the last release** (16 → 17 → 18…). Android only installs an
     update over an existing install when its `versionCode` is higher. The workflow checks this against the
     `output-metadata.json` of the latest release and fails if it isn't.
   - `versionName` is the version users see (for example `3.0.1`).
2. Optionally add release notes in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: the first
   line is the title (`3.0.1 – Short description`), the following `- ` lines become the release notes.
3. Commit, then tag and push:

```bash
git tag v3.0.1
git push origin v3.0.1
```

Tags must start with `v`. A tag containing `-` (for example `v3.1.0-beta1`) is published as a pre-release.
Pre-releases need a higher `versionCode` too.

### What the workflow does

`.github/workflows/android-release.yml` runs on every `v*` tag:

1. Fails right away if any of the four secrets is missing. Nothing is published.
2. Fails if `versionCode` is not higher than the latest release's.
3. Decodes the keystore to a temporary file outside the checkout and builds with
   `./gradlew assembleRelease -PrequireReleaseKey=true`, which fails instead of falling back to the debug key.
4. Runs `apksigner verify` on the APK and refuses to publish one signed with the Android debug key.
5. Creates the GitHub release with `alternate-<tag>.apk` and `output-metadata.json` attached.

`.github/workflows/build-apk.yml` builds an APK on every push and pull request and attaches it to the run as an
artifact. It uses the release key when the secrets are available and otherwise the debug key. A debug-signed
APK installs for testing but cannot update a release install. It is never published as a release.

## Installing for the first time

This fork is signed with a different key than the upstream Alternate app, and both use the same package name
(`com.lulu786.Alternate`). Android refuses to update an app with an APK from a different signer, so once:

1. Back up anything you want to keep from the installed app (for example, export contacts).
2. Uninstall the existing Alternate app.
3. Install `alternate-<tag>.apk` from this fork's releases.

After that, every new release installs over the previous one.

## Building a signed release locally

Create `android/keystore.properties` (ignored by git) pointing at your keystore:

```properties
storeFile=/home/you/alternate-release/alternate-release.jks
storePassword=...
keyAlias=alternate
keyPassword=...
```

A relative `storeFile` is resolved from `android/app`. Instead of the file you can set the
`RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`
environment variables (or Gradle properties). Then:

```bash
cd android
./gradlew assembleRelease -PrequireReleaseKey=true
"$ANDROID_HOME"/build-tools/<version>/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Without a release key, `assembleRelease` signs with the debug key (`android/app/debug.keystore`) unless
`-PrequireReleaseKey=true` is passed.

## Troubleshooting

1. **"Missing repository secrets"**: add the secrets from step 2.
2. **Keystore decoding fails**: `KEYSTORE_BASE64` must be the base64 of the `.jks` file on a single line.
3. **Signing fails**: check the password and the alias (`keytool -list -keystore <file>` shows the alias).
4. **"versionCode ... is not higher"**: bump `versionCode`, commit, delete the tag
   (`git push --delete origin <tag>` and `git tag -d <tag>`), and tag again.
5. **"App not installed" on the phone**: the installed app is signed with a different key (for example the
   upstream app or a debug build). Uninstall it and install the release APK.
