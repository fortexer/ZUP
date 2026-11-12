# Release signing for ZUP (Myket)

The release job in `.github/workflows/build.yml` signs the APK with a keystore stored ONLY in GitHub Secrets.
It refuses to run unless all four secrets exist. Nothing secret is in the repository.

## Secrets (Settings → Secrets and variables → Actions → New repository secret)
| Name | Value |
|---|---|
| ANDROID_KEYSTORE_BASE64 | the keystore file encoded as one-line base64 |
| KEYSTORE_PASSWORD | keystore password |
| KEY_ALIAS | key alias (e.g. `zup`) |
| KEY_PASSWORD | key password |

## Create the keystore ONCE (no local install needed)
1. Repo page → **Code → Codespaces → Create codespace on main** (cloud terminal with JDK).
2. In its terminal:
   `keytool -genkeypair -v -keystore zup-release.jks -alias zup -keyalg RSA -keysize 2048 -validity 10000`
   Choose strong passwords and write them down.
3. `base64 -w0 zup-release.jks` → copy the output into the `ANDROID_KEYSTORE_BASE64` secret.
4. Download `zup-release.jks` (Explorer → right-click → Download) and store it in at least two safe places
   (password manager / encrypted drive) together with the passwords and alias. **Never commit it.**
5. Delete the file in the Codespace and delete the Codespace.

Every future update MUST use this same keystore. If it is lost, Myket will not accept updates to the app.
Never generate a new key per build.
