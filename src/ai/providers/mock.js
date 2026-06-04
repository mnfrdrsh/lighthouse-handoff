// src/ai/providers/mock.js
// Mock AI Provider — returns deterministic recommendations from the normalized
// LighthouseSummary without calling any external service.
// Use this for development/testing and as the reference implementation.

/** @typedef {import('../types.js').AIProvider} AIProvider */
/** @typedef {import('../types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../types.js').AIAnalysis} AIAnalysis */
/** @typedef {import('../types.js').RankedIssue} RankedIssue */

import { buildRankedIssueList } from '../../lighthouse/scoring.js';

/**
 * @implements {AIProvider}
 */
export class MockProvider {
  get name() { return 'mock'; }

  /**
   * @param {LighthouseSummary} summary
   * @returns {Promise<AIAnalysis>}
   */
  async analyze(summary) {
    const rankedIssues = buildRankedIssueList(summary);
    const criticalAndHigh = rankedIssues.filter(i => i.priority === 'critical' || i.priority === 'high');
    const medium = rankedIssues.filter(i => i.priority === 'medium');

    return {
      executiveSummary: buildExecutiveSummary(summary),
      quickWins:        buildQuickWins(summary, medium),
      priorityFixes:    buildPriorityFixes(criticalAndHigh.slice(0, 5)),
      acceptanceCriteria: buildAcceptanceCriteria(summary),
    };
  }
}

// ---------------------------------------------------------------------------
// Deterministic builders
// ---------------------------------------------------------------------------

/**
 * @param {LighthouseSummary} summary
 * @returns {string}
 */
function buildExecutiveSummary(summary) {
  const { scores, metrics, strategy } = summary;
  const perfLabel = scoreLabel(scores.performance);
  const lcpSec    = (metrics.lcp / 1000).toFixed(2);
  const clsVal    = metrics.cls.toFixed(3);
  const tbtMs     = Math.round(metrics.tbt);

  const lines = [
    `**${strategy.toUpperCase()}** performance is **${perfLabel}** (${scores.performance}/100).`,
    `Accessibility: ${scores.accessibility}/100 · SEO: ${scores.seo}/100 · Best Practices: ${scores.bestPractices}/100.`,
    '',
    `Core Web Vitals snapshot: LCP ${lcpSec}s · CLS ${clsVal} · TBT ${tbtMs}ms.`,
  ];

  if (scores.performance < 50) {
    lines.push('', 'Performance is critically low. Significant gains are achievable through JavaScript optimisation and image delivery improvements.');
  } else if (scores.performance < 90) {
    lines.push('', 'There is meaningful room for improvement. Focus on the priority fixes below to move the score into the green range.');
  } else {
    lines.push('', 'Performance is already excellent. Review the items below to maintain this standard.');
  }

  return lines.join('\n');
}

/**
 * @param {LighthouseSummary} summary
 * @param {RankedIssue[]} mediumIssues
 * @returns {string[]}
 */
function buildQuickWins(summary, mediumIssues) {
  const wins = [];

  // Always-applicable quick wins
  wins.push('Enable text compression (Brotli or gzip) for all text-based assets if not already active');
  wins.push('Set long-lived Cache-Control headers on static assets with content hashes in their filenames');
  wins.push('Add `font-display: swap` to all @font-face declarations to prevent invisible text during load');

  // Conditional on scores
  if (summary.scores.accessibility < 90) {
    wins.push('Ensure all informative images have descriptive `alt` attributes (accessibility quick win)');
  }
  if (summary.scores.seo < 90) {
    wins.push('Verify each page has a unique, descriptive `<title>` and `<meta name="description">` tag');
  }

  // Surface medium-priority audit wins
  for (const issue of mediumIssues.slice(0, 3)) {
    wins.push(`Address "${issue.title}" — ${issue.displayValue || 'see report details'}`);
  }

  return wins;
}

/**
 * @param {RankedIssue[]} issues
 * @returns {import('../types.js').PriorityFix[]}
 */
function buildPriorityFixes(issues) {
  return issues.map(issue => ({
    id:          issue.id,
    title:       issue.title,
    reasoning:   buildReasoning(issue),
    instructions: buildInstructions(issue),
  }));
}

/**
 * @param {LighthouseSummary} summary
 * @returns {string[]}
 */
function buildAcceptanceCriteria(summary) {
  const { metrics } = summary;
  const criteria = [];

  criteria.push(`LCP must be below 2.5s (current: ${(metrics.lcp / 1000).toFixed(2)}s)`);
  criteria.push(`CLS must be below 0.1 (current: ${metrics.cls.toFixed(3)})`);
  criteria.push(`TBT must be below 200ms (current: ${Math.round(metrics.tbt)}ms)`);
  criteria.push('No new console errors or warnings introduced by changes');
  criteria.push('All critical user flows (forms, navigation, checkout) must function identically after changes');
  criteria.push('Re-run Lighthouse Handoff on the same URL + strategy to verify improvement');

  if (summary.scores.performance < 90) {
    criteria.push(`Performance score must reach ≥ 90 (current: ${summary.scores.performance})`);
  }

  return criteria;
}

// ---------------------------------------------------------------------------
// Per-issue helpers
// ---------------------------------------------------------------------------

/**
 * @param {RankedIssue} issue
 * @returns {string}
 */
function buildReasoning(issue) {
  const priorityMap = {
    critical: 'This is a critical issue that is severely impacting user experience and Core Web Vitals.',
    high:     'This high-priority issue has a significant impact on performance and should be addressed promptly.',
    medium:   'This medium-priority issue represents a meaningful but non-urgent optimisation opportunity.',
    low:      'This low-priority issue has a minor impact and can be addressed when convenient.',
  };

  let base = priorityMap[issue.priority] ?? priorityMap.medium;

  if (issue.displayValue) {
    base += ` Measured impact: ${issue.displayValue}.`;
  }

  if ((issue.affectedStrategies ?? []).length > 1) {
    base += ' Affects both mobile and desktop.';
  }

  return base;
}

/**
 * Returns specific, actionable instructions for known audit IDs,
 * or a generic fallback for unknown ones.
 *
 * @param {RankedIssue} issue
 * @returns {string[]}
 */
function buildInstructions(issue) {
  const id = issue.id ?? '';
  const offenders = extractOffenders(issue.items);

  if (id.includes('unused-javascript')) {
    const base = [
      'Open DevTools → Coverage tab and record a page load to identify unused JS bytes',
      'Apply route-based code-splitting via dynamic `import()` to defer non-critical chunks',
      'Remove dead code paths or replace heavy libraries with lighter alternatives',
      'Re-run the audit to confirm reduction in unused bytes',
    ];
    if (offenders) base.unshift(`Investigate these specific scripts first:\n   ${offenders}`);
    return base;
  }

  if (id.includes('third-party-summary')) {
    const base = [
      'Identify the third-party scripts causing the most blocking time',
      'Determine if these scripts can be deferred, loaded asynchronously, or removed',
      'Consider using a tag manager to control script execution priority',
    ];
    if (offenders) base.unshift(`Focus on these highest-impact third parties:\n   ${offenders}`);
    return base;
  }

  if (id.includes('render-blocking')) {
    const base = [
      'Identify render-blocking stylesheets and scripts in the Network waterfall',
      'Inline critical CSS for above-the-fold content and defer the full stylesheet',
      'Add `defer` or `async` to non-critical script tags',
      'Use the `media="print"` + JS `onload` pattern for non-critical CSS',
    ];
    if (offenders) base.unshift(`Target these specific render-blocking resources:\n   ${offenders}`);
    return base;
  }

  if (/largest-contentful-paint|__lcp/.test(id)) {
    return [
      'Inspect above-the-fold content and identify the LCP candidate (hero image, heading, etc.)',
      'If the LCP element is an image: set explicit width/height, avoid lazy-loading it, consider `fetchpriority="high"`, and use responsive sizes',
      'If the LCP element is text: ensure web fonts are preloaded or use `font-display: swap`',
      'Reduce TTFB: enable server-side caching or move compute closer to users',
      'Validate by rerunning Lighthouse and confirming LCP improves',
    ];
  }

  if (/cumulative-layout-shift|__cls/.test(id)) {
    return [
      'Add explicit `width` and `height` attributes to all `<img>` and `<video>` elements',
      'Reserve space for dynamically injected content (ads, embeds) using CSS `aspect-ratio` or min-height',
      'Use `font-display: optional` or preload web fonts to prevent FOIT/FOUT shifts',
      'Validate by visually inspecting the page load and checking the DevTools CLS metric',
    ];
  }

  if (/interaction-to-next-paint|__inp/.test(id)) {
    return [
      'Break up long tasks on the main thread using `scheduler.yield()` or `setTimeout` chunking',
      'Move heavy computation to a Web Worker',
      'Avoid synchronous layout reads during event handlers (no `offsetWidth` / `getBoundingClientRect` mid-write)',
    ];
  }

  if (id.includes('uses-optimized-images') || id.includes('modern-image-formats')) {
    const base = [
      'Convert images to WebP (and AVIF where supported) using `sharp` or an image CDN',
      'Serve responsive images with `srcset` + `sizes` to avoid oversized downloads on smaller screens',
      'Lazy-load all images below the fold with native `loading="lazy"`',
    ];
    if (offenders) base.unshift(`Optimize these specific images first:\n   ${offenders}`);
    return base;
  }
  
  if (id.includes('image-alt')) {
    const base = [
      'Ensure all informative images have short, descriptive alternate text',
      'For decorative images, use `alt=""` so screen readers ignore them',
    ];
    if (offenders) base.unshift(`Fix these specific elements:\n   ${offenders}`);
    return base;
  }

  if (id.includes('server-response-time')) {
    return [
      'Measure TTFB using WebPageTest or DevTools → Network → first document response',
      'Enable server-side caching (Redis, Varnish, or CDN edge cache) for the document response',
      'Consider moving to edge functions or a CDN POP closer to your primary user base',
    ];
  }

  if (id.includes('unused-css') || id.includes('unused-stylesheet')) {
    const base = [
      'Use PurgeCSS, UnCSS, or your framework\'s built-in CSS tree-shaking to remove unused rules',
      'Extract critical (above-the-fold) CSS and inline it; defer the rest',
    ];
    if (offenders) base.unshift(`Review these specific stylesheets:\n   ${offenders}`);
    return base;
  }

  // Generic fallback
  return [
    `Open DevTools and inspect the "${issue.title}" audit for the specific resources or elements flagged`,
    'Implement the smallest targeted change that addresses the root cause',
    'Validate by rerunning Lighthouse Handoff after the fix to verify the score improves',
  ];
}

/**
 * Extracts a formatted list of offenders (URLs or Node labels) from audit items.
 *
 * @param {any[]=} items
 * @returns {string}
 */
function extractOffenders(items) {
  if (!items || !items.length) return '';
  return items.map(item => {
    let name = item.url || item.node?.nodeLabel || item.groupLabel || '';
    if (name) {
      // Truncate long URLs to just the file name if possible
      try {
        if (name.startsWith('http')) {
          const url = new URL(name);
          name = url.pathname.split('/').pop() || name;
        }
      } catch (e) {
        // Ignore parsing errors
      }
      return '- ' + name;
    }
    return '';
  }).filter(Boolean).join('\n   ');
}

/**
 * @param {number} score
 * @returns {string}
 */
function scoreLabel(score) {
  if (score >= 90) return 'excellent 🟢';
  if (score >= 50) return 'needs improvement 🟡';
  return 'poor 🔴';
}
