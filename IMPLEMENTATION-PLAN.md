# Lighthouse Handoff — Implementation Plan

**Project**: `google-psi-to-md`  
**Working Name**: Lighthouse Handoff  
**Goal**: Turn Google PageSpeed Insights (PSI) / Lighthouse audits into high-quality, coding-agent-ready Markdown reports.

---

## 1. Project Overview

This Chrome extension lets users:
1. Open any website
2. Click the extension
3. Select Mobile/Desktop + audit categories
4. Generate a structured Markdown report optimized for coding agents (not just raw Lighthouse data)
5. Download the report as `.md`

The core value is **translation** — not just showing PSI scores, but giving clear, prioritized, safe instructions a coding agent can execute.

---

## 2. Goals & Success Criteria

### MVP Success (Phase 4 complete)
- User can generate a useful agent-ready report for any public URL
- Report contains: scores, prioritized issues, specific agent instructions, acceptance criteria, and guardrails
- Works for both Mobile and Desktop strategies
- Clean, professional Markdown output
- Extension installs and runs without errors on Chrome 120+

### Stretch Goals
- Multiple report styles (Agent Brief, Human Summary, Brutal Executive)
- Reasonable error handling and rate limit messaging
- Good visual feedback during API calls

---

## 3. Architecture Decisions (MVP)

| Decision                  | Choice                                      | Rationale |
|---------------------------|---------------------------------------------|---------|
| Extension Type            | Manifest V3 Chrome Extension                | Required for modern Chrome |
| Build System (MVP)        | None (vanilla JS/HTML/CSS)                  | Fastest path to working prototype |
| Language                  | JavaScript (no TypeScript initially)        | Speed of iteration |
| API Key Handling          | User provides their own PSI API key         | Avoids key leakage + respects Google ToS |
| Storage                   | `chrome.storage.sync`                       | Syncs across devices, simple |
| Background Logic          | Service Worker (`background.js`)            | Manifest V3 requirement |
| Report Generation         | Pure client-side JS (`report-builder.js`)   | No backend for MVP |
| Markdown Output           | Hand-written template strings + helpers     | Full control, zero dependencies |
| Permissions               | `activeTab`, `storage`, `downloads` + host permission for googleapis | Minimum required |

**Important**: We deliberately avoid any bundler or framework in the first 3–4 phases to stay extremely fast.

---

## 4. Phased Implementation Plan

### Phase 0: Foundation & Scaffolding

**Objective**: Get a loadable, installable extension skeleton.

**Files to Create**:
- `manifest.json`
- `README.md` (project overview + setup instructions)
- `icons/icon-16.png`, `icons/icon-48.png`, `icons/icon-128.png` (can use placeholders)
- `.gitignore` (if we later add node_modules)
- `IMPLEMENTATION-PLAN.md` (this file)

**Deliverables**:
- Extension loads in Chrome (`chrome://extensions` → Load unpacked)
- No console errors on install
- Basic popup opens when icon is clicked

**Demo Criteria**: Extension icon appears, popup opens (even if empty).

---

### Phase 1: Popup UI Foundation

**Objective**: Build the complete user interface for the MVP.

**Files to Create**:
- `popup.html`
- `popup.css`
- `popup.js`
- `options.html` (simple page for API key entry)
- `options.css`
- `options.js`

**Key UI Elements** (in popup):
- Current tab URL (auto-detected, read-only with copy button)
- Manual URL override input
- Strategy checkboxes: **Mobile** + **Desktop** (both default on)
- Category checkboxes:
  - Performance (default on)
  - Accessibility
  - Best Practices
  - SEO
- API Key status indicator (shows whether key is configured)
- Primary button: **Generate Agent Report**
- Secondary: **Open Options** (for key management)
- Status / loading area (spinner + messages)

**Files to Modify**: `manifest.json` (add `action`, `options_page`, permissions)

**Deliverables**:
- Clean, usable popup UI
- URL detection working via `chrome.tabs`
- Form state persists in memory for the session
- Clicking "Open Options" opens the options page

**Demo Criteria**: User can see current URL, toggle options, and click the generate button (it just logs for now).

---

### Phase 2: PSI API Integration Layer

**Objective**: Successfully call the PageSpeed Insights API and return raw data.

**Files to Create**:
- `background.js` (service worker)
- `api/psi-client.js` (clean API wrapper)

**Key Work**:
- Implement `callPSI(url, strategy, categories[])` function
- Handle authentication via API key
- Proper error handling (400, 429 rate limits, network errors)
- Support multiple categories in a single call (PSI allows `&category=performance&category=accessibility` etc.)
- Message passing between popup ↔ background

**Files to Modify**:
- `manifest.json` — add `"background"`, host permissions for `https://www.googleapis.com/*`
- `popup.js` — wire generate button to send message to background

**Deliverables**:
- Can successfully fetch a PSI result for a real URL (mobile + performance only first)
- Errors surface nicely in the UI (rate limit, invalid key, etc.)

**Demo Criteria**: Click Generate → see raw JSON appear in console or a temporary debug panel.

---

### Phase 3: Core Report Generation (Highest Value Phase)

**Objective**: Convert raw PSI data into excellent, agent-ready Markdown.

**Files to Create**:
- `report-builder.js` (the heart of the product)
- `templates/` folder (optional later — start with functions inside the file)

**Critical Work in This Phase**:
- Parse `lighthouseResult` deeply:
  - `categories` scores
  - `audits` (especially `opportunities`, `diagnostics`, `metrics`)
  - `stackPacks`, `environment`, etc.
- Implement priority scoring logic (impact × effort)
- Generate these sections (minimum):
  1. Executive Summary (scores table)
  2. Core Web Vitals (when present)
  3. Highest-Impact Issues (ranked)
  4. Easy Wins
  5. Medium Fixes
  6. Hard / Architectural Fixes
  7. Mobile vs Desktop Differences (when both run)
  8. Agent Task List (actionable bullets)
  9. Guardrails (non-negotiable rules)
  10. Acceptance Criteria + Retest Instructions
- Add clear "Coding Agent Instructions" blocks for top issues

**Deliverables**:
- A genuinely useful Markdown file (not just data dump)
- Guardrails section is always present and strong

**Demo Criteria**: Generate a report for a real site (e.g. a slow marketing site) and the output looks like something you'd actually hand to Claude/Cursor/Grok.

---

### Phase 4: Full Feature Completion + Polish

**Objective**: Ship a complete, usable MVP.

**Tasks**:
- Support running **both** Mobile + Desktop in one click (parallel or smart sequential)
- Implement all four categories properly
- Wire up actual `.md` file download using `chrome.downloads`
- Add basic loading states, progress indication, and cancel support
- Handle the case where user has no API key gracefully
- Add simple "Report Style" selector (start with two):
  - **Agent Brief** (default — detailed instructions)
  - **Human Summary** (shorter, friendlier)
- Improve error messages and rate limit UX
- Add basic result preview in popup (first 20 lines + "Download full report")
- Add "Copy to Clipboard" button

**Files to Create / Update**:
- Enhance `report-builder.js` with style variants
- `utils/markdown.js` (small helper utilities if needed)
- Update `popup.js` + `popup.html` for new controls

**Deliverables**:
- End-to-end working flow: detect → configure → generate → download real `.md`
- Report quality is the standout feature

**Demo Criteria**: Record a 60-second demo of using the extension on a real site and getting a good report.

---

### Phase 5: Hardening, Packaging & Release Prep

**Objective**: Make it reliable and ready for others to use.

**Tasks**:
- Comprehensive error handling and logging
- Add user-visible rate limit guidance ("You're hitting limits — consider throttling or using a backend later")
- Create proper icon set (or use a good temporary one)
- Write clear `README.md` with:
  - How to get a PSI API key
  - How to install (Load unpacked)
  - Limitations
- Add basic analytics-free usage stats (optional, local only)
- Test on several real-world sites (fast, slow, SPA, marketing, etc.)
- Create a `PRIVACY.md` (important for Chrome Web Store later)
- Decide on final name + update all references

**Deliverables**:
- Production-quality MVP
- Documentation that lets another developer install and use it in < 5 minutes

---

## 5. Final Target File Structure (After Phase 5)

```
google-psi-to-md/
├── IMPLEMENTATION-PLAN.md
├── project-details.md
├── README.md
├── PRIVACY.md
├── .gitignore
│
├── manifest.json
│
├── popup.html
├── popup.css
├── popup.js
│
├── options.html
├── options.css
├── options.js
│
├── background.js
│
├── report-builder.js
├── psi-client.js
│
├── utils/
│   ├── markdown.js
│   └── formatters.js
│
├── icons/
│   ├── icon-16.png
│   ├── icon-48.png
│   └── icon-128.png
│
└── assets/                 # future screenshots, etc.
```

**Note**: `utils/` and extra files can be introduced gradually. Start flat if it feels faster.

---

## 6. Key Technical Components

### PSI API
- Endpoint: `https://www.googleapis.com/pagespeedonline/v5/runPagespeed`
- Key parameters: `url`, `strategy`, `category[]`, `key`
- Response shape to master: `lighthouseResult.categories`, `lighthouseResult.audits`

### Message Flow (MVP)
```
popup.js → chrome.runtime.sendMessage
background.js (service worker) → calls PSI → returns result
popup.js receives result → calls report-builder.js → triggers download
```

### Report Quality Bar
The generated Markdown must pass this test:
> "If I gave this to a strong coding agent with no other context, could it make meaningful improvements without breaking the site?"

---

## 7. Risks & Mitigations

| Risk                              | Likelihood | Mitigation |
|-----------------------------------|------------|----------|
| PSI API key management friction   | High       | Excellent onboarding in options page + clear README |
| Rate limiting during development  | Medium     | Cache responses locally during testing; warn users |
| Lighthouse JSON is extremely deep | High       | Phase 3 must include focused parsing — don't try to handle every audit |
| Overly aggressive agent fixes     | Medium     | Extremely strong guardrails section + explicit "do not" list |
| Manifest V3 service worker quirks | Medium     | Keep background logic minimal and well-tested |
| Chrome Web Store review (future)  | Low        | Start with "Load unpacked" distribution |

---

## 8. Testing Approach

- **Manual testing** on 8–10 real sites of varying quality
- **Edge cases**:
  - Very fast sites (few opportunities)
  - Sites with heavy third-party scripts
  - Sites that return errors from PSI
  - Invalid / missing API key
- Keep a small set of "golden" URLs for regression during development
- After Phase 4, do a full end-to-end test with a real coding agent (Cursor/Claude) using one of the generated reports

---

## 9. Future Phases (Post-MVP)

### Backend Track (when ready to productize)
- User accounts + saved reports
- API key pooling / rotation
- Before/after comparison
- Team workspaces
- Scheduled audits
- Webhook / agent integration

### Nice-to-Have Features
- Local caching of recent reports
- "Apply these fixes" suggestions that integrate with Cursor/Cline
- Export to Notion / Linear / GitHub issues
- Dark mode for the popup
- Firefox support

---

## 10. Open Questions (to resolve during implementation)

1. Should we allow running without an API key at all (limited free tier)?
2. How aggressive should the priority ranking algorithm be?
3. Do we want to support authenticated pages (cookies) in the MVP? (Complex)
4. Should the extension remember the last used settings per domain?
5. Final product name?

---

## Next Step Recommendation

**Start with Phase 0 + Phase 1 in one focused session.**

Once the popup UI is pleasant and the extension loads cleanly, Phase 2 (API) becomes very satisfying because you immediately see real data.

---

**Status**: Ready to begin implementation.

**Owner**: TBD  
**Last Updated**: 2026-05-31
