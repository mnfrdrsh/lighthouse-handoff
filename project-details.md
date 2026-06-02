---

## The Core Idea

The extension would do this:

1. User opens the target website.
2. Clicks the extension.
3. Extension grabs the current tab URL.
4. Runs PageSpeed Insights API for:

   * Mobile
   * Desktop
   * Performance
   * Accessibility
   * Best Practices
   * SEO
5. Parses the JSON response.
6. Converts the findings into a structured Markdown report.
7. Downloads something like:

```txt
lemonteed-pagespeed-agent-report.md
```

The PageSpeed Insights API supports running Lighthouse audits against a URL, accepts `desktop` or `mobile` strategy, and can return Performance, Accessibility, Best Practices, and SEO category data. It can also be used with or without an API key, though Google recommends a key for frequent automated usage. ([Google for Developers][1]) ([Google for Developers][2])

---

## Best Build Approach

### Option A — Chrome Extension Only

This is the lean MVP.

**Pros**

* Fast to build.
* No backend required.
* User owns their reports.
* Simple install/use flow.
* Perfect for your “hand it to a coding agent” use case.

**Cons**

* API key management is awkward.
* Heavy use may hit API limits.
* Public extension with embedded API key is not ideal.

Chrome extensions now use **Manifest V3**, and Chrome’s current model uses service workers instead of old long-running background pages. ([Chrome for Developers][3]) Every extension also needs a `manifest.json` file at the root. ([Chrome for Developers][4])

This is the version I’d build first.

---

### Option B — Extension + Backend

This is the “real product” version.

The extension is just the interface. Your backend handles:

* API keys
* Rate limits
* User accounts
* Report history
* Saved audits
* Team reports
* Before/after comparisons
* Agent-ready exports

**This is better if you want to turn it into a SaaS.**

---

## What the Extension Should Generate

The `.md` file should not just dump raw PageSpeed data. That’s rookie mode.

It should translate the report into a **coding-agent-ready implementation document**.

Example structure:

```md
# PageSpeed Optimization Report

URL: https://example.com
Generated: 2026-05-31
Strategies: Mobile + Desktop

## 1. Executive Summary

- Mobile Performance Score:
- Desktop Performance Score:
- Accessibility:
- Best Practices:
- SEO:

## 2. Highest-Impact Issues

### 1. Reduce unused JavaScript
Category: Performance
Affected: Mobile + Desktop
Severity: High
Difficulty: Medium

Problem:
The page loads JavaScript that is not needed during initial render.

Why it matters:
Unused JavaScript increases load time, blocks main-thread work, and delays interactivity.

Recommended fix:
- Remove unused scripts.
- Defer non-critical scripts.
- Split heavy scripts by page.
- Audit third-party tags.

Coding Agent Instructions:
Inspect all scripts loaded on this page. Identify files that are not needed for initial render. Defer, remove, or conditionally load them. Preserve existing functionality.

Acceptance Criteria:
- No broken interactive behavior.
- Lighthouse unused JS warning is reduced.
- Mobile performance score improves or remains stable.
```

That’s the magic: not “PageSpeed says bad,” but **“agent, go do this safely.”**

---

## Suggested Report Sections

I’d structure every generated `.md` like this:

| Section                       | Purpose                                          |
| ----------------------------- | ------------------------------------------------ |
| Executive Summary             | Quick score overview                             |
| Core Web Vitals               | LCP, CLS, INP/FID-style metrics where available  |
| Priority Fixes                | Ordered by impact                                |
| Easy Wins                     | Image sizes, alt text, meta tags, font-display   |
| Medium Fixes                  | JS cleanup, render-blocking assets, lazy loading |
| Hard Fixes                    | Architecture, server, CDN, third-party scripts   |
| Mobile vs Desktop Differences | Important because mobile usually gets smoked     |
| Agent Task List               | Direct implementation checklist                  |
| Acceptance Criteria           | How to know the fix worked                       |
| Retest Instructions           | Run PSI again after implementation               |

---

## MVP Feature Set

Build the first version with:

### Extension UI

* Current URL auto-detect
* Manual URL input
* Checkboxes:

  * Mobile
  * Desktop
  * Performance
  * Accessibility
  * Best Practices
  * SEO
* Button: **Generate Agent Report**
* Button: **Download `.md`**

### Generated Output

* Scores
* Failed audits
* Opportunities
* Diagnostics
* Estimated savings where available
* Priority ranking
* Agent-ready instructions
* Acceptance criteria

### Nice Touch

Add a toggle:

```txt
Report Style:
[ ] Human-readable
[ ] Coding agent brief
[ ] Brutal executive summary
```

Because sometimes you need “optimize images,” and sometimes you need “your homepage is wearing ankle weights.”

---

## The Product Angle

This could become more than a utility.

Possible names:

* **SpeedBrief**
* **Audit2Agent**
* **PageSpeed Brief**
* **Lighthouse Handoff**
* **AgentFix Report**
* **Score Surgery**
* **LemonLift** if it lives under Lemonteed

My favorite for your vibe:

## **Lighthouse Handoff**

Tagline:

> Turn PageSpeed reports into coding-agent-ready fix briefs.

Clean. Useful. No fluff.

---

## The Real Value

PageSpeed already tells people what is wrong.

The missing layer is:

> “What exactly should I tell my coding agent to fix, in what order, without breaking the site?”

That’s the product.

The extension should not compete with PageSpeed. It should **translate PageSpeed into implementation language.**

---

## How I’d Build the MVP

### Files

```txt
/pagespeed-agent-extension
  manifest.json
  popup.html
  popup.css
  popup.js
  background.js
  report-builder.js
  icons/
```

### Permissions

Likely:

```json
{
  "permissions": ["activeTab", "downloads", "storage"],
  "host_permissions": [
    "https://www.googleapis.com/*"
  ]
}
```

### API Call Shape

Conceptually:

```js
https://www.googleapis.com/pagespeedonline/v5/runPagespeed
  ?url=https://example.com
  &strategy=mobile
  &category=performance
  &category=accessibility
  &category=best-practices
  &category=seo
```

Then repeat for desktop.

---

## One Important Warning

Do **not** rely only on PageSpeed’s “opportunities.” Some coding agents will over-fix and make a mess.

The generated report should include guardrails like:

```md
Do not redesign the page.
Do not change brand styling unless required.
Do not remove tracking scripts without approval.
Do not replace the framework.
Do not install new packages unless necessary.
Preserve current layout and functionality.
```

That part matters. Otherwise the agent may “optimize” the site like a raccoon with a keyboard.

---
