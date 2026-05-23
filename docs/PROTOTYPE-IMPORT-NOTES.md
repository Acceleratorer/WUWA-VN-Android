# WUWA VN Patcher Prototype Import Notes

Source artifacts:

- `wuwa-vn-patcher.zip`
- `wuwa-vn-patcher(fixed).zip`

This zip is a separate Android prototype, not production-ready code for the current app. Keep the zip untracked and use it as reference material only.

## Fixed Zip Check

`wuwa-vn-patcher(fixed).zip` was compared against the first zip. The only file content change found was `app/src/main/AndroidManifest.xml`.

The fixed manifest adds:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

The fixed zip still has the same prototype caveats listed below, so it should remain reference-only.

## Useful Ideas To Reuse

- Jetpack Compose app shell with `ComponentActivity`, `setContent`, Material 3, and bottom navigation.
- Screen split: Dashboard, Security, Diagnostics.
- ViewModel + `StateFlow` pattern for UI state.
- Diagnostics and security-audit presentation patterns.
- Compose screenshot testing concept with Robolectric/Roboazzi.
- Material 3 theme scaffolding and icon-heavy action layout.

## Do Not Copy Directly

- Package/application IDs are placeholder values: `com.example` and `com.aistudio.wuwavn.pxzwtq`.
- Manifest uses `allowBackup=true`; current production app requires `allowBackup=false`.
- Prototype version is `1.0` and does not use this repo's `version.properties`.
- Prototype patch flow includes SAF and ROOT modes; current app release policy should remain Shizuku-only for writes.
- Some patch operations are simulated rather than real Shizuku writes.
- Release metadata uses fallback/fake SHA values in places; production must keep verified SHA checks.
- Prototype has AI Studio/Gemini/secrets scaffolding that is not needed for WUWA VN.
- Prototype dependency set is much larger than the production app needs.
- Text assets show mojibake in extracted files and need UTF-8 cleanup before reuse.

## Current Production Safety That Must Stay

- Max Graphics remains `LOCKED`.
- No new Android permission without an explicit milestone.
- No `REQUEST_INSTALL_PACKAGES`.
- No arbitrary path write.
- Preset/template targets stay allowlisted.
- PAK/config SHA verification stays mandatory.
- Release CI must continue validating version, package, permission, debuggable state, and APK SHA.

## Suggested Migration Path

1. Add Compose dependencies and a minimal Compose shell behind existing runtime logic.
2. Recreate current home actions as Compose screens without changing controllers/writers.
3. Add Android Studio `@Preview` composables for onboarding, status, setup checklist, and blocked states.
4. Add screenshot tests for the main screens.
5. Keep release verification and safety CI unchanged.
6. Only after UI parity is verified, remove the legacy programmatic View UI.

## Release Direction

For the next release, prefer a narrow milestone:

- `v3.3.15`: Compose preview foundation, no runtime behavior changes.
- `v3.3.16`: Compose home screen parity, still no writer changes.
- `v3.3.17`: Non-tech install/onboarding polish.
