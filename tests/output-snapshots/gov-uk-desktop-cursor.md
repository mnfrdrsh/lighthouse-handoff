You are a performance engineering expert working inside Cursor.
Your task: implement the highest-priority performance fixes for https://www.gov.uk/.
Strategy analysed: DESKTOP.

## Context

**DESKTOP** performance is **excellent 🟢** (97/100).
Accessibility: 97/100 · SEO: 92/100 · Best Practices: 95/100.

Core Web Vitals snapshot: LCP 0.98s · CLS 0.010 · TBT 40ms.

Performance is already excellent. Review the items below to maintain this standard.

## Approach

Work through the priority fixes below in order.
For each fix:
1. Identify the relevant files by searching the codebase (use Cursor's @codebase or grep)
2. Determine the probable root cause from the reasoning provided
3. Implement the smallest targeted change that addresses the issue
4. Do NOT change unrelated code, visual design, or third-party integrations

## Priority Fixes



## Quick Wins (after priority fixes)

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add `font-display: swap` to all @font-face declarations to prevent invisible text during load

## Acceptance Criteria

- [ ] LCP must be below 2.5s (current: 0.98s)
- [ ] CLS must be below 0.1 (current: 0.010)
- [ ] TBT must be below 200ms (current: 40ms)
- [ ] No new console errors or warnings introduced by changes
- [ ] All critical user flows (forms, navigation, checkout) must function identically after changes
- [ ] Re-run Lighthouse Handoff on the same URL + strategy to verify improvement

## Guardrails

- Do NOT redesign layouts, spacing, or visual hierarchy
- Do NOT change brand colors, typography, or existing UI components
- Do NOT remove or disable analytics or tracking scripts without explicit approval
- Do NOT introduce new third-party dependencies
- Preserve all existing functionality and interactive behaviour
- When in doubt, make the smallest possible change
