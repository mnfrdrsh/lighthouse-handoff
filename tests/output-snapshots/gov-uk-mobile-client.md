# Website Performance Report

**Website**: https://www.gov.uk/  
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
| Speed | 85/100 | ⚠️ Needs Work |
| Accessibility | 97/100 | ✅ Excellent |
| Search Engine Friendliness | 92/100 | ✅ Excellent |
| Best Practices | 95/100 | ✅ Excellent |

MOBILE performance is needs improvement 🟡 (85/100).
Accessibility: 97/100 · SEO: 92/100 · Best Practices: 95/100.

Core Web Vitals snapshot: LCP 2.80s · CLS 0.020 · TBT 320ms.

There is meaningful room for improvement. Focus on the priority fixes below to move the score into the green range.

---

## What We Will Fix First

These issues have the biggest impact on how fast your site feels to visitors.
Each fix will be tested carefully before going live.

### 1. Largest Contentful Paint

**Why it matters**: This is a critical issue that is severely impacting user experience and Google speed metrics. Measured impact: 2.8 s.

**What we will do**: Identify the page load speed element using developer tools → Google's speed test tool or Performance panel

### 2. Remove unused website code

**Why it matters**: This high-priority issue has a significant impact on performance and should be addressed promptly. Measured impact: 0.38 s potential savings.

**What we will do**: Open developer tools → Coverage tab and record a page load to identify unused website code bytes

---

## Quick Improvements

Smaller improvements our team can make in under a day:

- Enable text compression (Brotli or gzip) for all text-based assets if not already active
- Set long-lived Cache-Control headers on static assets with content hashes in their filenames
- Add "font-display: swap" to all @font-face declarations to prevent invisible text during load

---

## How We Will Know It Worked

After the improvements are live, we will re-test the website and check that:

- page load speed must be below 2.5s (current: 2.80s)
- visual stability must be below 0.1 (current: 0.020)
- page responsiveness must be below 200ms (current: 320ms)
- No new console errors or warnings introduced by changes
- All critical user flows (forms, navigation, checkout) must function identically after changes
- Re-run our tool on the same URL + strategy to verify improvement
- Performance score must reach ≥ 90 (current: 85)

---

*This report was produced by Lighthouse Handoff and reviewed by your development team.*
