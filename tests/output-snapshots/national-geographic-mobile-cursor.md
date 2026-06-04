You are a performance engineering expert working inside Cursor.
Your task: implement the highest-priority performance fixes for https://www.nationalgeographic.com/.
Strategy analysed: MOBILE.

## Context

**MOBILE** performance is **poor 🔴** (25/100).
Accessibility: 79/100 · SEO: 87/100 · Best Practices: 78/100.

Core Web Vitals snapshot: LCP 12.60s · CLS 0.320 · TBT 1800ms.

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

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 0.32.

**Implementation steps**:
   1. Add explicit `width` and `height` attributes to all `<img>` and `<video>` elements
   2. Reserve space for dynamically injected content (ads, embeds) using CSS `aspect-ratio` or min-height
   3. Use `font-display: optional` or preload web fonts to prevent FOIT/FOUT shifts
   4. Validate by visually inspecting the page load and checking the DevTools CLS metric

### Fix 2: Largest Contentful Paint (`largest-contentful-paint`)

**Reasoning**: This is a critical issue that is severely impacting user experience and Core Web Vitals. Measured impact: 12.6 s.

**Implementation steps**:
   1. Inspect above-the-fold content and identify the LCP candidate (hero image, heading, etc.)
   2. If the LCP element is an image: set explicit width/height, avoid lazy-loading it, consider `fetchpriority="high"`, and use responsive sizes
   3. If the LCP element is text: ensure web fonts are preloaded or use `font-display: swap`
   4. Reduce TTFB: enable server-side caching or move compute closer to users
   5. Validate by rerunning Lighthouse and confirming LCP improves

### Fix 3: Efficiently encode images (`uses-optimized-images`)

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 2.4 MiB potential savings.

**Implementation steps**:
   1. Optimize these specific images first:
   - hero-gallery-main-2024.jpg
   - wildlife-photo-of-year.jpg
   - nature-explorer-banner.jpg
   2. Convert images to WebP (and AVIF where supported) using `sharp` or an image CDN
   3. Serve responsive images with `srcset` + `sizes` to avoid oversized downloads on smaller screens
   4. Lazy-load all images below the fold with native `loading="lazy"`

### Fix 4: Serve images in next-gen formats (`modern-image-formats`)

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 2.1 MiB potential savings.

**Implementation steps**:
   1. Optimize these specific images first:
   - hero-gallery-main-2024.jpg
   - wildlife-photo-of-year.jpg
   - nature-explorer-banner.jpg
   2. Convert images to WebP (and AVIF where supported) using `sharp` or an image CDN
   3. Serve responsive images with `srcset` + `sizes` to avoid oversized downloads on smaller screens
   4. Lazy-load all images below the fold with native `loading="lazy"`

### Fix 5: Eliminate render-blocking resources (`render-blocking-resources`)

**Reasoning**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: Potential savings of 1.2 s.

**Implementation steps**:
   1. Target these specific render-blocking resources:
   - application.css
   - fonts.css
   2. Identify render-blocking stylesheets and scripts in the Network waterfall
   3. Inline critical CSS for above-the-fold content and defer the full stylesheet
   4. Add `defer` or `async` to non-critical script tags
   5. Use the `media="print"` + JS `onload` pattern for non-critical CSS

## Quick Wins (after priority fixes)

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add `font-display: swap` to all @font-face declarations to prevent invisible text during load
- Ensure all informative images have descriptive `alt` attributes (accessibility quick win)
- Verify each page has a unique, descriptive `<title>` and `<meta name="description">` tag
- Address "Defer offscreen images" — 890 KiB potential savings
- Address "Properly size images" — 1.1 MiB potential savings
- Address "Total Blocking Time" — 1,800 ms

## Acceptance Criteria

- [ ] LCP must be below 2.5s (current: 12.60s)
- [ ] CLS must be below 0.1 (current: 0.320)
- [ ] TBT must be below 200ms (current: 1800ms)
- [ ] No new console errors or warnings introduced by changes
- [ ] All critical user flows (forms, navigation, checkout) must function identically after changes
- [ ] Re-run Lighthouse Handoff on the same URL + strategy to verify improvement
- [ ] Performance score must reach ≥ 90 (current: 25)

## Guardrails

- Do NOT redesign layouts, spacing, or visual hierarchy
- Do NOT change brand colors, typography, or existing UI components
- Do NOT remove or disable analytics or tracking scripts without explicit approval
- Do NOT introduce new third-party dependencies
- Preserve all existing functionality and interactive behaviour
- When in doubt, make the smallest possible change
