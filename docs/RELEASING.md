# Releasing

Releases are built by GitHub Actions when you push a version tag.

## One-time: create a signing key

```bash
./scripts/generate-keystore.sh
```

Back up the `.jks` file and its password somewhere safe. If you lose it, you can never publish an update that installs over existing copies of the app. Then add four repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 northvane-release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `northvane` (or what you chose) |
| `RELEASE_KEY_PASSWORD` | the key password |

Without these, tagged releases still work but are attached as clearly named debug-signed APKs.

## Each release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move the Unreleased notes in `CHANGELOG.md` under a new `## [x.y.z] - date` heading. The release workflow uses that section as the release notes.
3. Commit, then tag and push:

   ```bash
   git tag v1.0.1
   git push origin main v1.0.1
   ```
4. The **Release** workflow runs the tests, builds the APK, writes `SHA256SUMS.txt` and publishes the release. Tags containing a hyphen (for example `v1.1.0-beta.1`) are marked as pre-releases.

## Verifying a download

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs Northvane-v1.0.0.apk
```

The signing certificate fingerprint should be the same on every release.
