# PlexonSpawners Release Policy

## Stable-only production releases

Production releases use stable semantic versions such as `4.0.0`, `4.0.1`, and `4.1.0`.
The production path does not publish RC tags, preview tags, snapshots, candidate releases or GitHub
prereleases.

Development can occur on feature/hardening branches. The published artifact is a full stable release
only after source review, tests and distribution checks pass.

## Stable source invariant

A stable release is produced from one exact commit where:

```text
main == release/stable
```

The release workflow rebuilds that exact source and verifies the non-prerelease version, complete
Gradle test/check/JAR, expected test suite, Java compatibility, dependency boundaries, removed
architecture absence, non-shading, deterministic JAR SHA-256, public non-prerelease release state and
downloaded-asset checksums.

Stable tags are immutable-intent.

## Deployment validation

Environment-specific server validation remains required operational practice for major architecture
migrations, but it is separate from SemVer release status.

For 4.0.0 use `docs/MIGRATION_4_0.md` and
`.release/RUNTIME_CERTIFICATION_4.0.0.template`.

Never mark deployment certification complete without real server evidence.
