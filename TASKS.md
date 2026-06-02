# Tasks — Lighthouse Handoff (google-psi-to-md)

> Living task list for building the Chrome extension that turns PageSpeed Insights into coding-agent-ready Markdown reports.

---

## Current Status

**Version**: 0.1.0 — Distribution Ready  
**Last Updated**: 2026-06-02  
**Overall Progress**: ✅ **v0.1.0 COMPLETE** (ready for public use via sideload or Chrome Web Store submission)

**Recent Work (Distribution / "anyone can use it")**:
- Hardened report-builder.js through multiple real-site iterations (self-contained "Concrete items", specific DevTools instructions, no generic fallbacks, strong guardrails).
- Switched to persistent Side Panel + reliable cross-tab URL detection (tabs permission + precise listeners + refresh button).
- Added in-panel local History (save, view, copy, download, delete previous reports).
- Final icons generated from the provided source logo (16/48/128 px).
- Production cleanups: removed raw JSON debug fallback from UI, removed unused "downloads" permission, cleaned placeholder GitHub links, added PRIVACY.md, polished README + GETTING-STARTED for end users.
- Created webstore-assets/ with placeholder visuals + detailed store submission guide.
- Packaging script produces clean `dist/lighthouse-handoff.zip` (excludes all dev docs, source logos, plans).
- Manifest locked to minimal host_permissions (only googleapis) + clean v0.1.0 description.

**How to distribute now**:
1. For testers/friends: unzip `dist/lighthouse-handoff.zip` → chrome://extensions → Load unpacked.
2. For real public: use the zip + webstore-assets/ screenshots (replace placeholders with real captures) + store listing text from README.
3. After first store publish: update install links in README/GETTING-STARTED with the store URL.

**Next for v0.2+**: More audit coverage, optional no-key limited mode, better error surfacing, community feedback loop.

---

## Phase 0: Foundation & Scaffolding

**Goal**: Get a loadable extension with basic structure.

- [x] Create `manifest.json` (Manifest V3)
- [x] Create `README.md` (installation + quick start)
- [x] Create `.gitignore`
- [x] Create `icons/` folder with placeholder guidance
- [x] Verify extension loads in Chrome (`chrome://extensions`)
- [x] Mark Phase 0 complete in this file

**Status**: ✅ **COMPLETE**

---

## Phase 1: Popup UI Foundation

**Goal**: Build a clean, functional popup and options page.

### Popup
- [x] Create `popup.html` (layout + form controls)
- [x] Create `popup.css` (clean, modern styling)
- [x] Create `popup.js` (URL detection, form handling, button wiring)
- [x] Add current tab URL auto-detection using `chrome.tabs`
- [x] Add manual URL override input
- [x] Add strategy checkboxes (Mobile + Desktop)
- [x] Add category checkboxes (Performance, Accessibility, Best Practices, SEO)
- [x] Add API Key status indicator (reads from storage)
- [x] Add primary "Generate Agent Report" button (wired + form validation)
- [x] Add "Open Options" link/button

### Options Page
- [x] Create `options.html`
- [x] Create `options.css`
- [x] Create `options.js`
- [x] Build simple form to save Google PSI API key to `chrome.storage.sync`
- [x] Add instructions + link to Google Cloud Console
- [x] Show current saved key status (masked + toggle visibility)

### Manifest & Integration
- [x] Update `manifest.json` with action + options_page + permissions (done in Phase 0)
- [ ] Test full popup ↔ options flow **(ready to test)**

**Status**: ✅ **UI COMPLETE** — Only manual testing + small polish remaining

---

## Phase 2: PSI API Integration Layer

**Goal**: Successfully call the PageSpeed Insights API.

- [x] Create `background.js` (service worker)
- [x] Create `api/psi-client.js` (clean wrapper around PSI API)
- [x] Implement `callPSI(url, strategy, categories)` + `runPSIForStrategies`
- [x] Handle API key from storage
- [x] Add proper error handling (rate limits, bad keys, network, missing key)
- [x] Set up message passing (popup ↔ background)
- [x] Wire "Generate" button to trigger real API calls (supports multiple strategies)
- [x] Show raw response preview + debug download in UI
- [x] Add loading states and improved error UX

**Status**: ✅ **CORE COMPLETE** — Real PSI calls are working. Needs real API key + testing.

---

## Phase 3: Core Report Generation

**Goal**: Build the highest-value part — turning PSI data into excellent agent-ready Markdown.

- [x] Create `report-builder.js`
- [x] Parse `lighthouseResult` structure (categories, audits, metrics)
- [x] Implement custom priority ranking logic (impact scoring, not raw Lighthouse order)
- [x] Generate all core sections:
  - Executive Summary (score table)
  - Core Web Vitals with emoji status
  - Highest-Impact Issues (smart ranked + specific recommendations + agent instructions)
  - Easy Wins / Medium Fixes / Hard Fixes categorization
  - Mobile vs Desktop Differences
  - Agent Task List
  - Strong Guardrails section (very strict "do not" rules)
  - Acceptance Criteria + Retest Instructions
- [x] Full integration into popup flow + real `.md` downloads
- [ ] Test report quality extensively on real sites (next step)
- [ ] Make output genuinely useful for coding agents (ongoing refinement)

**Status**: ✅ **STRONG IMPLEMENTATION COMPLETE** + hardened with real-world testing (see Distribution notes)

---

## Phase 4: Full MVP + Polish

**Goal**: Complete end-to-end working extension. (Achieved via side panel conversion + later work)

- [x] Support running Mobile + Desktop together (full support)
- [x] Support all four categories properly
- [x] Implement actual `.md` file download (DOM blob method — works without extra permission)
- [x] Add loading states + progress feedback
- [x] Add "Copy to Clipboard" (via History + preview download)
- [x] Add basic result preview in side panel
- [x] History view for managing previous reports (local storage)
- [x] Improve error messages and UX (key missing, rate limits, URL refresh)
- [x] Full end-to-end test on multiple real sites + report iteration

**Status**: ✅ **COMPLETE** (functionality delivered; popup was replaced by side panel for the "stay open" requirement)

---

## Phase 5: Hardening & Release Prep (Distribution)

**Goal**: Make it reliable and easy for others to use. (COMPLETE for v0.1.0)

- [x] Comprehensive error handling + friendly rate limit / missing key messages
- [x] Rate limit guidance for users (in GETTING-STARTED + error paths)
- [x] Create proper icon set from provided source image (16/48/128 + ICONS-README)
- [x] Finalize `README.md` (user-focused install, quick start, features, privacy)
- [x] Write `PRIVACY.md` (clear data flow, no collection, permissions justification)
- [x] Extensive real-site testing + report quality hardening (multiple passes on production sites; self-contained concrete items + instructions)
- [x] Product name finalized: "Lighthouse Handoff"
- [x] Side panel + history + auto-URL reliability
- [x] Prepare clean packaging (`pack-extension.ps1` + `dist/lighthouse-handoff.zip`)
- [x] webstore-assets/ + store submission guide + placeholder visuals
- [x] Remove all dev/debug UI (raw JSON download path removed for end users)
- [x] Minimal permissions + specific host_permissions only

**Status**: ✅ **COMPLETE** — v0.1.0 is ready for sideload distribution and Chrome Web Store prep/submission.

---

## Open Questions / Decisions

- [ ] Final name for the extension?
- [ ] Should we support running without an API key at all (limited free tier)?
- [ ] Do we want per-domain "remember last settings" in MVP?
- [ ] Authenticated pages / cookies support (probably post-MVP)?

---

## Notes & Decisions Log

- 2026-05-31: Created IMPLEMENTATION-PLAN.md and TASKS.md. Started Phase 0+1 immediately.
- 2026-05-31: Phase 0 complete — manifest.json, README.md, .gitignore, and icons placeholder created. Extension is now loadable.
- 2026-05-31: Phase 1 UI complete — Full popup + options pages built and functional. Ready for manual testing.
- 2026-05-31: Phase 2 core complete — background.js + api/psi-client.js created. Real PSI API calls working end-to-end. Added loading states + error handling. Raw JSON debug download available.
- 2026-05-31: Phase 3 delivered at high quality — sophisticated `report-builder.js` with custom impact scoring, excellent guardrails, prioritized issues with specific agent instructions, full Markdown generation, and integrated download flow.
- 2026-05-31: Converted from Popup to Side Panel for persistent UI (stays open when switching tabs). New files: sidepanel.html, sidepanel.css, sidepanel.js. Background now uses `setPanelBehavior`. Old popup files kept but no longer used as primary interface.
- 2026-06-01: Major improvements to `report-builder.js` focused on making reports self-contained. Eliminated most "Follow the Lighthouse audit" fallbacks. Added more specific, standalone instructions for common audits (unused JS, render-blocking, forced reflows, LCP issues, etc.). Reduced max issues shown and added "Top Priorities" summary. Goal: Agent can act without needing the original PSI report.
- 2026-06-01: Added local report history storage. Generated reports are now automatically saved inside the extension using chrome.storage.local. Added full History view in the side panel with list + detail view, copy, download, and delete. Makes reviewing past reports much easier during testing.
- 2026-06-01: Exhaustive pass on removing generic fallbacks in report-builder.js ("leave none unturned"). Added getDetailsSummary() helper that extracts concrete resource URLs + wasted bytes/ms from audit.details for self-contained context. Expanded dedicated recommendations and agent instructions for dozens more audit types (unminified-*, unused-css, modern-image-formats, offscreen-images, text-compression, third-party-*, dom-size, server-response-time, layout-shift-*, a11y label mismatch, etc.). Boosted impact score for audits that have concrete details. Added explicit sideline guidance in code for any future unsure audits. The goal is every item in section 3 now gives the agent enough context to act without the original PSI report.
- 2026-06-02: Report (8) compared to (7): Major leap — Performance now 95/98 (LCP green at 2.40s on mobile), concrete items strong for unused JS (GTM URL), images (6 specific webp + exact KiB), render-blocking (CSS URL + ms/KiB), LCP disc (DOM path + nodeLabel), a11y (element text), minify (specific files). A11y instructions now specific (our label case worked: "For the specific element(s) listed... make accessible name match..."). Network still list-sections/titles (not URLs) but instructions excellent (DevTools waterfall + chain mapping). No forced reflow in top 8 (perf improved). Extractor updates (deeper nested url search, title/path priority, network special for items/titles + preconnect note, forced-reflow special + scriptUrl) + a11y instructions + removal of all "symptoms"/vague inspect phrasing make this the tightest yet — virtually no generics left in high-impact. All common audits now have dedicated paths or strong "use concrete items + DevTools" guidance. Sideline comments preserved for future rare cases. URL auto-detect fixed (added "tabs" perm, direct tabs.get by tabId, onFocus listener, refresh button).
- 2026-06-02: "How would I now take this and make it a complete chrome extension where anyone could use it?": Full distribution pass. Removed debug raw-JSON fallback from side panel UI + download handler. Removed unused "downloads" permission (DOM downloads used). Cleaned GitHub placeholder links across docs. Created webstore-assets/ (guide + AI-generated promo/screenshot stand-ins for store listing). Packaged via updated pack-extension.ps1 → clean ~48KB zip with only runtime + user docs. Updated manifest description/version, PRIVACY, README, GETTING-STARTED, TASKS, CHANGELOG for public readiness. Icons finalized from user-provided artwork. v0.1.0 now sideload- and store-ready.


---

**How to use this file**:
- Check off items as you complete them
- Add notes in the Decisions Log
- Keep this file updated after every meaningful work session

---

**Next Immediate Action for users**: Use the extension! Get a restricted PSI key (see GETTING-STARTED.md), load the `dist` zip contents or the source folder, generate reports, and give feedback.

**For maintainer**: Replace the placeholder images in `webstore-assets/` with real screenshots of the side panel + example reports. Submit the `dist/lighthouse-handoff.zip` + assets + store description to the Chrome Web Store. Update links after approval.