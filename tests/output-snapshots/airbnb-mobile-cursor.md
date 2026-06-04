You are a performance engineering expert working inside Cursor.
Your task: implement the highest-priority performance fixes for https://www.airbnb.com/.
Strategy analysed: MOBILE.

## Context

**MOBILE** performance is **poor 🔴** (30/100).
Accessibility: 88/100 · SEO: 92/100 · Best Practices: 92/100.

Core Web Vitals snapshot: LCP 7.80s · CLS 0.120 · TBT 2850ms.

Performance is critically low. Significant gains are achievable through JavaScript optimisation and image delivery improvements.

## Approach

Work through the priority fixes below in order.
For each fix:
1. Identify the relevant files by searching the codebase (use Cursor's @codebase or grep)
2. Determine the probable root cause from the reasoning provided
3. Implement the smallest targeted change that addresses the issue
4. Do NOT change unrelated code, visual design, or third-party integrations

## Priority Fixes

### Fix 1: Cumulative Layout Shift

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 0.12.

**Implementation steps**:
   1. Add explicit `width` and `height` attributes to all `<img>` and `<video>` elements
   2. Reserve space for dynamically injected content (ads, embeds) using CSS `aspect-ratio` or min-height
   3. Use `font-display: optional` or preload web fonts to prevent FOIT/FOUT shifts

### Fix 2: Largest Contentful Paint

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 7.8 s.

**Implementation steps**:
   1. Identify the LCP element using DevTools → Lighthouse or Performance panel
   2. Add `<link rel="preload" as="image">` for the LCP image in `<head>`
   3. Ensure the LCP resource is not lazy-loaded
   4. Reduce TTFB: enable server-side caching or move compute closer to users

### Fix 3: Remove unused JavaScript

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 4.1 s potential savings.

**Implementation steps**:
   1. Open DevTools → Coverage tab and record a page load to identify unused JS bytes
   2. Apply route-based code-splitting via dynamic `import()` to defer non-critical chunks
   3. Remove dead code paths or replace heavy libraries with lighter alternatives
   4. Re-run the audit to confirm reduction in unused bytes

### Fix 4: Eliminate render-blocking resources

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: Potential savings of 2.2 s.

**Implementation steps**:
   1. Identify render-blocking stylesheets and scripts in the Network waterfall
   2. Inline critical CSS for above-the-fold content and defer the full stylesheet
   3. Add `defer` or `async` to non-critical script tags
   4. Use the `media="print"` + JS `onload` pattern for non-critical CSS

## Quick Wins (after priority fixes)

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add `font-display: swap` to all @font-face declarations to prevent invisible text during load
- Ensure all informative images have descriptive `alt` attributes (accessibility quick win)
- Address "Minimize main-thread work" — 9.8 s
- Address "Reduce JavaScript execution time" — 7.2 s
- Address "Reduce the impact of third-party code" — 18 third-parties, 2.1 s blocking

## Acceptance Criteria

- [ ] LCP must be below 2.5s (current: 7.80s)
- [ ] CLS must be below 0.1 (current: 0.120)
- [ ] TBT must be below 200ms (current: 2850ms)
- [ ] No new console errors or warnings introduced by changes
- [ ] All critical user flows (forms, navigation, checkout) must function identically after changes
- [ ] Re-run Lighthouse Handoff on the same URL + strategy to verify improvement
- [ ] Performance score must reach ≥ 90 (current: 30)

## Guardrails

- Do NOT redesign layouts, spacing, or visual hierarchy
- Do NOT change brand colors, typography, or existing UI components
- Do NOT remove or disable analytics or tracking scripts without explicit approval
- Do NOT introduce new third-party dependencies
- Preserve all existing functionality and interactive behaviour
- When in doubt, make the smallest possible change
