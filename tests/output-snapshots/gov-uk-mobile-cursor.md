You are a performance engineering expert working inside Cursor.
Your task: implement the highest-priority performance fixes for https://www.gov.uk/.
Strategy analysed: MOBILE.

## Context

**MOBILE** performance is **needs improvement 🟡** (85/100).
Accessibility: 97/100 · SEO: 92/100 · Best Practices: 95/100.

Core Web Vitals snapshot: LCP 2.80s · CLS 0.020 · TBT 320ms.

There is meaningful room for improvement. Focus on the priority fixes below to move the score into the green range.

## Approach

Work through the priority fixes below in order.
For each fix:
1. Identify the relevant files by searching the codebase (use Cursor's @codebase or grep)
2. Determine the probable root cause from the reasoning provided
3. Implement the smallest targeted change that addresses the issue
4. Do NOT change unrelated code, visual design, or third-party integrations

## Priority Fixes

### Fix 1: Largest Contentful Paint

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 2.8 s.

**Implementation steps**:
   1. Identify the LCP element using DevTools → Lighthouse or Performance panel
   2. Add `<link rel="preload" as="image">` for the LCP image in `<head>`
   3. Ensure the LCP resource is not lazy-loaded
   4. Reduce TTFB: enable server-side caching or move compute closer to users

### Fix 2: Remove unused JavaScript

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 0.38 s potential savings.

**Implementation steps**:
   1. Open DevTools → Coverage tab and record a page load to identify unused JS bytes
   2. Apply route-based code-splitting via dynamic `import()` to defer non-critical chunks
   3. Remove dead code paths or replace heavy libraries with lighter alternatives
   4. Re-run the audit to confirm reduction in unused bytes

## Quick Wins (after priority fixes)

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add `font-display: swap` to all @font-face declarations to prevent invisible text during load

## Acceptance Criteria

- [ ] LCP must be below 2.5s (current: 2.80s)
- [ ] CLS must be below 0.1 (current: 0.020)
- [ ] TBT must be below 200ms (current: 320ms)
- [ ] No new console errors or warnings introduced by changes
- [ ] All critical user flows (forms, navigation, checkout) must function identically after changes
- [ ] Re-run Lighthouse Handoff on the same URL + strategy to verify improvement
- [ ] Performance score must reach ≥ 90 (current: 85)

## Guardrails

- Do NOT redesign layouts, spacing, or visual hierarchy
- Do NOT change brand colors, typography, or existing UI components
- Do NOT remove or disable analytics or tracking scripts without explicit approval
- Do NOT introduce new third-party dependencies
- Preserve all existing functionality and interactive behaviour
- When in doubt, make the smallest possible change
