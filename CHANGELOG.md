# Changelog

All notable changes to Lighthouse Handoff will be documented here.

## [0.1.0] - 2026-06-02

### Added
- Side panel UI (stays open across tab switches) with auto URL detection + manual override.
- Options page for storing your own Google PageSpeed Insights API key.
- Automatic local storage of generated reports (History view inside the panel).
- High-quality, self-contained Markdown report generation focused on coding-agent actionability:
  - Concrete resource lists pulled from PSI details when available.
  - Specific DevTools-oriented instructions for common audits.
  - Strong guardrails section in every report.
- Packaging helper (`pack-extension.ps1`) for clean distribution zips.
- Icons from the official project logo (symbol version for small sizes).
- `PRIVACY.md`, updated user-focused `README.md` and `GETTING-STARTED.md`.
- MIT license and basic changelog.

### Notes
- Requires users to provide their own free PSI API key (no keys bundled).
- Pure Manifest V3, no build step required.
- This is the first version considered ready for public / "anyone could use it" distribution (sideload or future Chrome Web Store).

See [IMPLEMENTATION-PLAN.md](./IMPLEMENTATION-PLAN.md) for the original vision (internal).

Repository and Chrome Web Store links will be added upon first public publication.
