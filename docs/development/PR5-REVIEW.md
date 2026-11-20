# PR #5: release-signing review (draft, not posted)

Reviewed https://github.com/samuelbeckman/expressive-clock/pull/5, description, all three discussion comments, its sole commit a650901afa73d8f259ae156eb14da9c30d118ab1 and complete one-file diff. GitHub reports MERGEABLE; base is v0.1.5/dab8f44, while current main is v0.1.7/c509838. The signing block remained unchanged between these releases, so the contribution is still relevant; the version bump must be retained when updating/rebasing.

## Findings

1. **Release identity risk:** `signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")` silently signs the production package using a debug certificate when a keystore is absent or missing. Both release APK/AAB packaging can succeed with the wrong identity. Do not merge this fallback.
2. **Incomplete decoupling:** `hasValidKeystore` only tests path existence. If the file exists but passwords/alias are missing, the PR still throws during configuration, blocking debug, unit tests and lint. It doesn't validate the keystore or its identity.
3. The contributor goal is correct: private production signing must not be required to configure a contributor debug/test/lint build. No credential leakage or dependency change was found in the submitted patch. An accidental debug-signed production artifact is the principal distribution hazard.

## Local comparison candidate

`app/build.gradle.kts` now creates an optional/incomplete **release** signing configuration without eager `error` calls. Debug signing remains AGP's existing debug configuration. A separate `verifyProductionSigning` task guards release validation and APK/AAB packaging; it requires a real keystore file and all credential fields. Native AGP signing validation handles incorrect passwords/alias/keystore contents. There is **no release-to-debug fallback**. No signing values or private files are committed or logged.

This candidate is kept in the Gradle file only; the overlay/profile changes are in Kotlin files. A standalone patch is provided with the development evidence for comparison. The candidate does not change versionName/code, package IDs, dependencies, production key path precedence, or existing signing identity. No contributor branch or GitHub resource was modified.

Validation: separate temporary contributor copy, no keystore.properties/private keys/local.properties and a separate Gradle user home without production properties. assembleDebug, testDebugUnitTest, lintDebug and lintRelease passed. assembleRelease and bundleRelease failed at verifyProductionSigning as expected. An explicitly missing key path still allowed assembleDebug and blocked assembleRelease. An existing empty test keystore with missing credentials allowed debug and blocked AAB packaging. Local maintainer release builds passed and the APK certificate remains 87d7f3d030fb89f3a780f196092e804ac5c2058292fc319dab4aaa5c62f2ec22.

## Suggested review comment — awaiting approval

Thanks @junksidetm for narrowing this to signing and making contributor builds easier. The change is still useful after v0.1.7, and I agree that debug builds, unit tests and lint should run without our private production key.

Before merging, could we remove the release-to-debug signing fallback? A release task should fail clearly when production signing is unavailable; it should never silently create a debug-signed APK or AAB using the production package name. Also, file existence doesn't establish a valid signing setup, and the eager password/alias errors still block contributor checks when a key path exists but credentials are incomplete.

The smallest correction is to configure the release signing fields optionally, retain the release signing configuration (never substitute debug), and defer required-credential/file checks to release packaging/signing tasks. AGP can then validate the actual key/password when those tasks run. Please keep debug/test/lint independent of that validation. We'd verify missing credentials, a missing key path and incomplete credentials: contributor checks should pass, while release APK and AAB packaging must fail. With maintainer credentials, both artifacts must retain the existing production certificate. Thanks again for the focused contribution!
