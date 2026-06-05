# PageSpeed Optimization Report

**URL**: https://lemonteed.com/  
**Generated**: 2026-06-05  
**Strategies**: mobile + desktop  
**Tool**: Lighthouse Handoff (PageSpeed Insights → Agent Brief)

## 1. Executive Summary

| Category            | Mobile | Desktop |
| ------------------- | ------ | ------- |
| Performance         | 75     | 97      |
| Accessibility       | 100    | 100     |
| Best practices      | 100    | 100     |
| Seo                 | 100    | 100     |

> **Interpretation**: Scores above 90 are excellent. 50–89 need work. Below 50 are critical.

## 2. Core Web Vitals
- **LCP (Largest Contentful Paint)**: Mobile 5.47s 🔴 | Desktop 1.18s 🟢
- **CLS (Cumulative Layout Shift)**: Mobile 0.000 🟢 | Desktop 0.000 🟢
- **FCP (First Contentful Paint)**: Mobile 2.43s 🟡 | Desktop 0.72s 🟢
- **TBT (Total Blocking Time)**: Mobile 0.06s 🟢 | Desktop 0.01s 🟢

> **Target thresholds**: LCP < 2.5s, CLS < 0.1, INP < 200ms for good scores.

## 3. Highest-Impact Issues (Ranked)

> This section is designed to be self-contained. You should not need the original Lighthouse report to act on these items.

**Top Priorities to Address First:**
1. Reduce unused JavaScript
2. Network dependency tree
3. Improve image delivery


### 1. Reduce unused JavaScript
**Impact**: High (100) | **Category**: performance | **Affected**: mobile + desktop
**Measured**: Est savings of 65 KiB


**Concrete items from the audit (top offenders):**
1. https://www.googletagmanager.com/gtag/js?id=G-CCB0LW648K — wasted ~65 KiB


**Problem**: Reduce unused JavaScript and defer loading scripts until they are required to decrease bytes consumed by network activity. [Learn how to reduce unused JavaScript](https://developer.chrome.com/docs/lighthouse/performance/unused-javascript/).

**Recommended Fix**:
- Identify the largest JavaScript files that are downloaded but never used on the initial page load.
- Remove dead code, or split it out using dynamic imports so it only loads when needed.
- Pay special attention to third-party scripts (analytics, chat widgets, ads, etc.) — many can be loaded after the critical rendering path or on user interaction.

**Coding Agent Instructions**:
1. Open the page and use the Network + Coverage tab in DevTools to identify JavaScript that is downloaded but never executed on initial load.
2. Prioritize the largest unused bundles.
3. Either remove the code entirely, code-split it with dynamic imports, or defer it if it is not needed for the initial render or critical user interactions.
4. Do **not** remove scripts that power key functionality (forms, navigation, tracking that must fire early).

### 2. Network dependency tree
**Impact**: High (95) | **Category**: performance | **Affected**: mobile + desktop


**Network dependency sections (from audit):**
1. list-section
2. Preconnected origins
3. Preconnect candidates
These often indicate preconnect or early resource opportunities. Use DevTools Network > Waterfall (filter by "Preconnect" or look at initiator chain) to see the actual request URLs and timings for the full dependency tree.

**Problem**: [Avoid chaining critical requests](https://developer.chrome.com/docs/performance/insights/network-dependency-tree) by reducing the length of chains, reducing the download size of resources, or deferring the download of unnecessary resources to improve page load.

**Recommended Fix**:
- Use the listed resources/chains (or the DevTools waterfall) to map every hop in the critical path.
- For early resources in the chain: inline small critical CSS/JS, add preload/preconnect, or move non-critical later.
- Shorten the chain length wherever possible.

**Coding Agent Instructions**:
1. Open DevTools Network tab, reload the page, and look at the "Waterfall" or use the "Critical request chains" view (or Lighthouse report details) to see the chain of blocking requests.
2. For each link in the chain: inline small critical resources, add <link rel="preload"> or <link rel="preconnect"> for key domains, or defer non-critical ones.
3. Reduce the number of hops by combining files or using HTTP/2 multiplexing where possible.

### 3. Improve image delivery
**Impact**: High (95) | **Category**: performance | **Affected**: mobile + desktop
**Measured**: Est savings of 74 KiB


**Concrete items from the audit (top offenders):**
1. https://lemonteed.com/images/home/icons/junk-drawer.webp — wasted ~11 KiB
2. https://lemonteed.com/images/lemonteedlogo-456.webp — wasted ~11 KiB
3. https://lemonteed.com/images/home/icons/fm-tower.webp — wasted ~11 KiB
4. https://lemonteed.com/images/home/icons/memetic-arena.webp — wasted ~10 KiB
5. https://lemonteed.com/images/home/icons/coming-soon-crater.webp — wasted ~9 KiB
6. https://lemonteed.com/images/home/lemon-mascot.webp — wasted ~9 KiB
... and 2 more item(s).


**Problem**: Reducing the download time of images can improve the perceived load time of the page and LCP. [Learn more about optimizing image size](https://developer.chrome.com/docs/performance/insights/image-delivery)

**Recommended Fix**:
- Convert large images to modern formats (WebP or AVIF) with appropriate quality settings.
- Use responsive images (`srcset` + `sizes`) so smaller devices don't download oversized images.
- Lazy load images that are below the fold.
- Consider using a proper image CDN or optimization service for future images.

**Coding Agent Instructions**:
Use the specific image URLs in the "Concrete items" list above. For each: convert to WebP/AVIF (with fallbacks), add proper srcset/sizes, and lazy-load only those below the fold. For the LCP image, ensure it is not lazy-loaded and is preloaded if possible.

### 4. LCP request discovery
**Impact**: High (95) | **Category**: performance | **Affected**: mobile + desktop


**Concrete items from the audit (top offenders):**
1. type: checklist
2. DOM path: 1,HTML,1,BODY,1,MAIN,0,SECTION,0,DIV,3,DIV,0,PICTURE,2,IMG


**Problem**: [Optimize LCP](https://developer.chrome.com/docs/performance/insights/lcp-discovery) by making the LCP image discoverable from the HTML immediately, and avoiding lazy-loading

**Recommended Fix**:
- Use the LCP element identification (from DevTools or the items above) to ensure its resource is referenced directly and early in the initial HTML.
- Avoid lazy-loading the actual LCP resource.
- Add <link rel="preload"> for the LCP image/font if it is not discovered early enough.

**Coding Agent Instructions**:
1. Identify what element is the Largest Contentful Paint (check mobile first, as it is usually worse).
2. Ensure the LCP image or text is loaded as early as possible (preload the resource if it's an image or font).
3. Reduce server response time (TTFB) if it's high.
4. Eliminate or defer any render-blocking resources that delay the LCP element.

### 5. Render-blocking requests
**Impact**: High (95) | **Category**: performance | **Affected**: mobile + desktop
**Measured**: Est savings of 150 ms


**Concrete items from the audit (top offenders):**
1. https://lemonteed.com/assets/css/world-map.css?v=2 — potential savings ~917ms — 5 KiB
2. https://lemonteed.com/assets/css/style.css?v=9 — potential savings ~307ms — 11 KiB


**Problem**: Requests are blocking the page's initial render, which may delay LCP. [Deferring or inlining](https://developer.chrome.com/docs/performance/insights/render-blocking) can move these network requests out of the critical path.

**Recommended Fix**:
- Defer non-critical CSS and JavaScript.
- Inline critical CSS for above-the-fold content.
- Use `media="print"` + JS onload trick for non-critical stylesheets.

**Coding Agent Instructions**:
1. Identify the resources (CSS/JS) that are blocking the initial render.
2. For non-critical CSS: defer it or load it asynchronously.
3. For critical CSS: inline the above-the-fold styles directly in the <head>.
4. For non-critical JavaScript: add defer or async attributes, or move it to the bottom of the body.

### 6. Largest Contentful Paint
**Impact**: High (75) | **Category**: performance | **Affected**: mobile + desktop
**Measured**: 5.5 s

**Problem**: Largest Contentful Paint marks the time at which the largest text or image is painted. [Learn more about the Largest Contentful Paint metric](https://developer.chrome.com/docs/lighthouse/performance/lighthouse-largest-contentful-paint/)

**Recommended Fix**:
- Optimize or preload the LCP image/element.
- Reduce server response time (TTFB).
- Eliminate render-blocking resources affecting the LCP element.

**Coding Agent Instructions**:
1. Identify what element is the Largest Contentful Paint (check mobile first, as it is usually worse).
2. Ensure the LCP image or text is loaded as early as possible (preload the resource if it's an image or font).
3. Reduce server response time (TTFB) if it's high.
4. Eliminate or defer any render-blocking resources that delay the LCP element.

### 7. Image elements do not have explicit `width` and `height`
**Impact**: Medium (65) | **Category**: performance | **Affected**: mobile + desktop


**Concrete items from the audit (top offenders):**
1. https://lemonteed.com/images/home/icons/what-if-woods.webp
2. https://lemonteed.com/images/home/icons/memetic-arena.webp
3. https://lemonteed.com/images/home/icons/archive-cavern.webp
4. https://lemonteed.com/images/home/icons/junk-drawer.webp
5. https://lemonteed.com/images/home/icons/vrg-vault.webp
6. https://lemonteed.com/images/home/icons/fm-tower.webp
... and 2 more item(s).


**Problem**: Set an explicit width and height on image elements to reduce layout shifts and improve CLS. [Learn how to set image dimensions](https://web.dev/articles/optimize-cls#images_without_dimensions)

**Recommended Fix**:
- Convert large images to modern formats (WebP or AVIF) with appropriate quality settings.
- Use responsive images (`srcset` + `sizes`) so smaller devices don't download oversized images.
- Lazy load images that are below the fold.
- Consider using a proper image CDN or optimization service for future images.

**Coding Agent Instructions**:
Use the specific image URLs in the "Concrete items" list above. For each: convert to WebP/AVIF (with fallbacks), add proper srcset/sizes, and lazy-load only those below the fold. For the LCP image, ensure it is not lazy-loaded and is preloaded if possible.

### 8. Minify CSS
**Impact**: Medium (65) | **Category**: performance | **Affected**: mobile + desktop
**Measured**: Est savings of 3 KiB


**Concrete items from the audit (top offenders):**
1. https://lemonteed.com/assets/css/style.css?v=9 — wasted ~3 KiB


**Problem**: Minifying CSS files can reduce network payload sizes. [Learn how to minify CSS](https://developer.chrome.com/docs/lighthouse/performance/unminified-css/).

**Recommended Fix**:
- Minify the CSS files listed.
- Most bundlers (Vite, Webpack, etc.) have built-in CSS minification — enable it.

**Coding Agent Instructions**:
Minification is almost always safe for production builds — just ensure your build process is correctly configured and source maps are available for debugging if you need to debug production code.

## 4. Easy Wins (Quick Improvements)

These are typically low-risk and fast to implement (often < 1 hour). The highest value work is listed in section 3 above.

- Ensure all informative images have meaningful `alt` text (Accessibility)
- Add or improve meta description and title tags if missing or weak (SEO)
- Enable text compression (gzip or Brotli) on the server for HTML/CSS/JS/SVG
- Set long Cache-Control headers (with content hashes in filenames) for static assets
- Use `font-display: swap` (or better) for web fonts to avoid invisible text

Run this report again after these changes.

## 5. Medium Fixes

These usually take more effort or coordination but have good ROI. Prioritize based on the specific items in section 3.

- JavaScript code-splitting + defer non-critical bundles (focus on the largest unused ones first)
- Proper image optimization pipeline (modern formats + responsive images + lazy loading below the fold)
- Eliminate or defer render-blocking CSS/JS for above-the-fold content
- Reduce impact of heavy third-party scripts (facades, delay until interaction, or remove)
- Improve caching strategy and CDN usage

## 6. Hard / Architectural Fixes

These are higher effort and should only be considered after the concrete items in section 3 are addressed and re-tested.

- Significant changes to JavaScript architecture (e.g. islands architecture, partial hydration, moving heavy libs to web workers)
- Adopting or improving SSR / edge rendering / streaming for the critical path
- Large-scale replacement or removal of heavy third-party dependencies
- Major overhaul of image pipeline / hosting (e.g. dedicated image CDN with on-the-fly optimization)
- Fundamental changes to how the critical rendering path is built (e.g. new bundling strategy, different framework rendering model)

## 7. Mobile vs Desktop Differences

- **Mobile Performance**: 75
- **Desktop Performance**: 97
- **Gap**: 22 points

Performance is relatively consistent across devices.

## 8. Agent Task List (Prioritized Order)

1. Start with the "Top Priorities to Address First" listed at the top of section 3. Use the "Concrete items from the audit" details for each one as your direct targets.
2. Focus on Performance issues first (they usually move the needle most for user experience and SEO).
3. Make one significant change at a time, then re-run this tool on the same URL/strategy to measure impact.
4. Never remove or heavily modify scripts, third-party code, or critical rendering paths without verifying the functionality still works (especially forms, navigation, and conversion tracking).
5. After your changes, re-generate the report with the exact same settings and compare.

## 9. Guardrails — Do NOT Do These Things

**Strict rules for any coding agent working on this site:**

- Do **not** redesign layouts, spacing, or visual hierarchy.
- Do **not** change brand colors, typography, or existing UI components unless explicitly required by a performance fix.
- Do **not** remove or disable analytics, tracking, or marketing scripts without explicit approval.
- Do **not** introduce new third-party dependencies or frameworks.
- Do **not** refactor large parts of the application "while you're in there".
- Do **not** change server configuration or deployment pipelines unless the audit specifically calls for it.
- Preserve all existing functionality and interactive behavior.
- When in doubt, make the smallest possible change that improves the metric.

These guardrails exist because aggressive "optimization" frequently destroys business value and user experience.

## 10. Acceptance Criteria & Retest Instructions

**After making changes:**

1. Re-run this extension on the same URL using the exact same settings.
2. The targeted audit(s) should show meaningful improvement (ideally move from red → yellow or yellow → green).
3. No new console errors should appear.
4. All critical user flows (forms, navigation, checkout, etc.) must continue to work exactly as before.
5. Core Web Vitals should not regress on the pages you modified.

**Retest command**: Use Lighthouse Handoff again with the same Mobile + Desktop + Performance settings and compare the new report against this one.

---

*Report generated by Lighthouse Handoff — turning raw PageSpeed data into actionable, safe implementation briefs for coding agents.*