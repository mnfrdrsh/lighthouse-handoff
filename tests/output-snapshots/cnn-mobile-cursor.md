You are a performance engineering expert working inside Cursor.
Your task: implement the highest-priority performance fixes for https://www.cnn.com/.
Strategy analysed: MOBILE.

## Context

**MOBILE** performance is **poor 🔴** (22/100).
Accessibility: 74/100 · SEO: 84/100 · Best Practices: 75/100.

Core Web Vitals snapshot: LCP 8.40s · CLS 0.380 · TBT 4200ms.

Performance is critically low. Significant gains are achievable through JavaScript optimisation and image delivery improvements.

## Approach

Work through the priority fixes below in order.
For each fix:
1. Identify the relevant files by searching the codebase (use Cursor's @codebase or grep)
2. Determine the probable root cause from the reasoning provided
3. Implement the smallest targeted change that addresses the issue
4. Do NOT change unrelated code, visual design, or third-party integrations

## Priority Fixes

### Fix 1: Cumulative Layout Shift (`cumulative-layout-shift`)

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 0.38.

**Implementation steps**:
   1. Add explicit `width` and `height` attributes to all `<img>` and `<video>` elements
   2. Reserve space for dynamically injected content (ads, embeds) using CSS `aspect-ratio` or min-height
   3. Use `font-display: optional` or preload web fonts to prevent FOIT/FOUT shifts
   4. Validate by visually inspecting the page load and checking the DevTools CLS metric

### Fix 2: Largest Contentful Paint (`largest-contentful-paint`)

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 8.4 s.

**Implementation steps**:
   1. Inspect above-the-fold content and identify the LCP candidate (hero image, heading, etc.)
   2. If the LCP element is an image: set explicit width/height, avoid lazy-loading it, consider `fetchpriority="high"`, and use responsive sizes
   3. If the LCP element is text: ensure web fonts are preloaded or use `font-display: swap`
   4. Reduce TTFB: enable server-side caching or move compute closer to users
   5. Validate by rerunning Lighthouse and confirming LCP improves

### Fix 3: Remove unused JavaScript (`unused-javascript`)

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 3.8 s potential savings.

**Implementation steps**:
   1. Investigate these specific scripts first:
   - main.chunk.js
   - streamsense.plugin.js
   - controltag.js
   2. Open DevTools → Coverage tab and record a page load to identify unused JS bytes
   3. Apply route-based code-splitting via dynamic `import()` to defer non-critical chunks
   4. Remove dead code paths or replace heavy libraries with lighter alternatives
   5. Re-run the audit to confirm reduction in unused bytes

### Fix 4: Eliminate render-blocking resources (`render-blocking-resources`)

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: Potential savings of 1.9 s.

**Implementation steps**:
   1. Target these specific render-blocking resources:
   - main.css
   - fonts.css
   2. Identify render-blocking stylesheets and scripts in the Network waterfall
   3. Inline critical CSS for above-the-fold content and defer the full stylesheet
   4. Add `defer` or `async` to non-critical script tags
   5. Use the `media="print"` + JS `onload` pattern for non-critical CSS

### Fix 5: Serve images in next-gen formats (`modern-image-formats`)

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 890 KiB potential savings.

**Implementation steps**:
   1. Optimize these specific images first:
   - 240601120000-cnn-hero.jpg
   - 240601100000-cnn-card1.jpg
   - 240601090000-cnn-card2.jpg
   2. Convert images to WebP (and AVIF where supported) using `sharp` or an image CDN
   3. Serve responsive images with `srcset` + `sizes` to avoid oversized downloads on smaller screens
   4. Lazy-load all images below the fold with native `loading="lazy"`

## Quick Wins (after priority fixes)

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add `font-display: swap` to all @font-face declarations to prevent invisible text during load
- Ensure all informative images have descriptive `alt` attributes (accessibility quick win)
- Verify each page has a unique, descriptive `<title>` and `<meta name="description">` tag
- Address "Avoid an excessive DOM size" — 4,800 elements
- Address "Enable text compression" — 340 KiB potential savings
- Address "Reduce the impact of third-party code" — 32 third-parties found, 4.8 s blocking

## Acceptance Criteria

- [ ] LCP must be below 2.5s (current: 8.40s)
- [ ] CLS must be below 0.1 (current: 0.380)
- [ ] TBT must be below 200ms (current: 4200ms)
- [ ] No new console errors or warnings introduced by changes
- [ ] All critical user flows (forms, navigation, checkout) must function identically after changes
- [ ] Re-run Lighthouse Handoff on the same URL + strategy to verify improvement
- [ ] Performance score must reach ≥ 90 (current: 22)

## Guardrails

- Do NOT redesign layouts, spacing, or visual hierarchy
- Do NOT change brand colors, typography, or existing UI components
- Do NOT remove or disable analytics or tracking scripts without explicit approval
- Do NOT introduce new third-party dependencies
- Preserve all existing functionality and interactive behaviour
- When in doubt, make the smallest possible change
