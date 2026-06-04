# Website Performance Report

**Website**: https://www.airbnb.com/  
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
| Speed | 30/100 | ❌ Needs Urgent Attention |
| Accessibility | 88/100 | ⚠️ Needs Work |
| Search Engine Friendliness | 92/100 | ✅ Excellent |
| Best Practices | 92/100 | ✅ Excellent |

MOBILE performance is poor 🔴 (30/100).
Accessibility: 88/100 · SEO: 92/100 · Best Practices: 92/100.

Core Web Vitals snapshot: LCP 7.80s · CLS 0.120 · TBT 2850ms.

Performance is critically low. Significant gains are achievable through JavaScript optimisation and image delivery improvements.

---

## What We Will Fix First

These issues have the biggest impact on how fast your site feels to visitors.
Each fix will be tested carefully before going live.

### 1. Cumulative Layout Shift

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 0.12.

**What we will do**: Add explicit "width" and "height" attributes to all "<img>" and "<video>" elements

### 2. Largest Contentful Paint

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 7.8 s.

**What we will do**: Inspect above-the-fold content and identify the page load speed candidate (hero image, heading, etc.)

### 3. Remove unused website code

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 4.1 s potential savings.

**What we will do**: Investigate these specific scripts first:
   - app-XXXXXX.js
   - vendor-react-XXXX.js
   - google-maps-XXXX.js

### 4. Eliminate render-blocking resources

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: Potential savings of 2.2 s.

**What we will do**: Target these specific render-blocking resources:
   - critical-XXXX.css
   - css2

---

## Quick Improvements

Smaller improvements our team can make in under a day:

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add "font-display: swap" to all @font-face declarations to prevent invisible text during load
- Ensure all informative images have descriptive "alt" attributes (accessibility quick win)
- Address "Minimize main-thread work" — 9.8 s
- Address "Reduce website code execution time" — 7.2 s
- Address "Reduce the impact of third-party code" — 18 third-parties, 2.1 s blocking

---

## How We Will Know It Worked

After the improvements are live, we will re-test the website and check that:

- page load speed must be below 2.5s (current: 7.80s)
- visual stability must be below 0.1 (current: 0.120)
- page responsiveness must be below 200ms (current: 2850ms)
- No new console errors or warnings introduced by changes
- All critical user flows (forms, navigation, checkout) must function identically after changes
- Re-run our tool on the same URL + strategy to verify improvement
- Performance score must reach ≥ 90 (current: 30)

---

*This report was produced by Lighthouse Handoff and reviewed by your development team.*
