# Website Performance Report

**Website**: https://www.wikipedia.org/  
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
| Speed | 58/100 | ⚠️ Needs Work |
| Accessibility | 81/100 | ⚠️ Needs Work |
| Search Engine Friendliness | 90/100 | ✅ Excellent |
| Best Practices | 83/100 | ⚠️ Needs Work |

MOBILE performance is needs improvement 🟡 (58/100).
Accessibility: 81/100 · SEO: 90/100 · Best Practices: 83/100.

Core Web Vitals snapshot: LCP 4.10s · CLS 0.070 · TBT 550ms.

There is meaningful room for improvement. Focus on the priority fixes below to move the score into the green range.

---

## What We Will Fix First

These issues have the biggest impact on how fast your site feels to visitors.
Each fix will be tested carefully before going live.

### 1. Cumulative Layout Shift

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 0.07.

**What we will do**: Add explicit "width" and "height" attributes to all "<img>" and "<video>" elements

### 2. Largest Contentful Paint

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 4.1 s.

**What we will do**: Identify the page load speed element using developer tools → Google's speed test tool or Performance panel

### 3. Eliminate render-blocking resources

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: Potential savings of 0.82 s.

**What we will do**: Identify render-blocking stylesheets and scripts in the Network waterfall

### 4. Remove unused website code

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 0.48 s potential savings.

**What we will do**: Open developer tools → Coverage tab and record a page load to identify unused website code bytes

### 5. Efficiently encode images

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 42 KiB potential savings.

**What we will do**: Convert images to WebP (and AVIF where supported) using "sharp" or an image content delivery network

---

## Quick Improvements

Smaller improvements our team can make in under a day:

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add "font-display: swap" to all @font-face declarations to prevent invisible text during load
- Ensure all informative images have descriptive "alt" attributes (accessibility quick win)
- Address "Image elements have [alt] attributes" — see report details
- Address "Remove unused visual styling code" — 98 KiB potential savings
- Address "Properly size images" — 28 KiB potential savings

---

## How We Will Know It Worked

After the improvements are live, we will re-test the website and check that:

- page load speed must be below 2.5s (current: 4.10s)
- visual stability must be below 0.1 (current: 0.070)
- page responsiveness must be below 200ms (current: 550ms)
- No new console errors or warnings introduced by changes
- All critical user flows (forms, navigation, checkout) must function identically after changes
- Re-run our tool on the same URL + strategy to verify improvement
- Performance score must reach ≥ 90 (current: 58)

---

*This report was produced by Lighthouse Handoff and reviewed by your development team.*
