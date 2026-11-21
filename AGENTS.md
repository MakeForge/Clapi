## AGENTS.md
See [DEVELOPMENT.md](./DEVELOPMENT.md) for general instructions

## Additional Agent rules
- See [.github/copilot-instructions.md](.github/copilot-instructions.md)

## Changelog & Versioning Policy

`.github/workflows/release.yml` extracts release notes directly from `CHANGELOG.md` using the `appVersion` in `gradle.properties` (matching `^## \[<appVersion>\]`) and validates that pushed Git tags (`v<appVersion>`) match `appVersion`.

After implementing any feature, bug fix, security fix, or notable CI/test/build fix (whether via Spec Kit or ad-hoc), you **MUST** update `CHANGELOG.md` (and `gradle.properties` when bumping versions):

1. **Section heading format**:
   - Unreleased version: `## [X.Y.Z] - TBD`
   - Released version: `## [X.Y.Z] - YYYY-MM-DD`
   - Keep versions ordered newest-first, separated by `---`.
2. **Subsection placement**:
   - `### New Features` — brief, concise description of user-facing features or enhancements, with a clickable link to the related GitHub issue when one exists (e.g., `[#42](https://github.com/MakeForge/Clapi/issues/42)`).
   - `### Fixes` — brief, concise description of bug fixes, UI fixes, security updates, and CI/test/build fixes, with GitHub issue links when applicable.
3. **Released vs. unreleased version check**:
   - Inspect the topmost `## [X.Y.Z] - ...` section in `CHANGELOG.md` and check Git tags (`git tag --list 'v*'`).
   - If the topmost section is **unreleased** (`TBD` and no `vX.Y.Z` Git tag exists), add the new entry under that section and verify `appVersion` in `gradle.properties` matches `X.Y.Z`.
     - If the current `TBD` section was only bumped for a `PATCH` release and you are adding a backward-compatible new feature (after `1.0.0` is released), promote the `TBD` version to the next `MINOR` version and update `appVersion` in `gradle.properties` accordingly.
   - If the topmost section is **already released** (has a concrete release date instead of `TBD`, or a matching `vX.Y.Z` Git tag already exists):
     1. Compute the next version using SemVer (`PATCH` for fixes, `MINOR` for backward-compatible features, `MAJOR` for breaking changes; must satisfy jpackage `MAJOR.MINOR.PATCH` with `MAJOR > 0`).
     2. Insert a new `## [X.Y.Z] - TBD` section at the top of `CHANGELOG.md` with `### New Features` and `### Fixes` headings.
     3. Update `appVersion=X.Y.Z` in `gradle.properties` to match.
