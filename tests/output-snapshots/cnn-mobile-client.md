# Website Performance Report

**Website**: https://www.cnn.com/  
**Report Date**: 2026-06-04  
**Device tested**: 📱 Mobile

---

## What This Report Tells You

We tested your website's speed and user experience using Google's Lighthouse tool.
Below is a plain-language summary of what we found and what our team plans to fix.

---

## Overall Health Check

| Area | Score | Status |
| ---- | ----- | ------ |
| Speed | 22/100 | ❌ Needs Urgent Attention |
| Accessibility | 74/100 | ⚠️ Needs Work |
| Search Engine Friendliness | 84/100 | ⚠️ Needs Work |
| Best Practices | 75/100 | ⚠️ Needs Work |

MOBILE performance is poor 🔴 (22/100).
Accessibility: 74/100 · SEO: 84/100 · Best Practices: 75/100.

Core Web Vitals snapshot: LCP 8.40s · CLS 0.380 · TBT 4200ms.

Performance is critically low. Significant gains are achievable through JavaScript optimisation and image delivery improvements.

---

## What We Will Fix First

These issues have the biggest impact on how fast your site feels to visitors.
Each fix will be tested carefully before going live.

### 1. Cumulative Layout Shift

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 0.38.

**What we will do**: Add explicit "width" and "height" attributes to all "<img>" and "<video>" elements

### 2. Largest Contentful Paint

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 8.4 s.

**What we will do**: Inspect above-the-fold content and identify the page load speed candidate (hero image, heading, etc.)

### 3. Remove unused website code

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 3.8 s potential savings.

**What we will do**: Investigate these specific scripts first:
   - main.chunk.js
   - streamsense.plugin.js
   - controltag.js

### 4. Eliminate render-blocking resources

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: Potential savings of 1.9 s.

**What we will do**: Target these specific render-blocking resources:
   - main.css
   - fonts.css

### 5. Serve images in next-gen formats

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 890 KiB potential savings.

**What we will do**: Optimize these specific images first:
   - 240601120000-cnn-hero.jpg
   - 240601100000-cnn-card1.jpg
   - 240601090000-cnn-card2.jpg

---

## Quick Improvements

Smaller improvements our team can make in under a day:

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add "font-display: swap" to all @font-face declarations to prevent invisible text during load
- Ensure all informative images have descriptive "alt" attributes (accessibility quick win)
- Verify each page has a unique, descriptive "<title>" and "<meta name="description">" tag
- Address "Avoid an excessive DOM size" — 4,800 elements
- Address "Enable text compression" — 340 KiB potential savings
- Address "Reduce the impact of third-party code" — 32 third-parties found, 4.8 s blocking

---

## How We Will Know It Worked

After the improvements are live, we will re-test the website and check that:

- page load speed must be below 2.5s (current: 8.40s)
- visual stability must be below 0.1 (current: 0.380)
- page responsiveness must be below 200ms (current: 4200ms)
- No new console errors or warnings introduced by changes
- All critical user flows (forms, navigation, checkout) must function identically after changes
- Re-run our tool on the same URL + strategy to verify improvement
- Performance score must reach ≥ 90 (current: 22)

---

*This report was produced by Lighthouse Handoff and reviewed by your development team.*
