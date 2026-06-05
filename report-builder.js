// report-builder.js
// Lighthouse Handoff — High-quality Markdown Report Generator
//
// Goal: Produce self-contained, actionable reports that a coding agent can use
// to make meaningful improvements WITHOUT needing access to the original
// Lighthouse / PageSpeed Insights report.

/**
 * Main entry point. Takes the results array from background.js and produces excellent Markdown.
 *
 * @param {Array} results - Array of { strategy, success, data } from psi-client
 * @param {string} url - The audited URL
 * @param {Object} [options]
 * @param {string} [options.style='agent'] - 'agent' | 'human' (future)
 * @returns {string} Complete Markdown report
 */
export function generateReport(results, url, options = {}) {
  const style = options.style || 'agent';
  const validResults = results.filter(r => r.success && r.data?.lighthouseResult);

  if (validResults.length === 0) {
    return generateErrorReport(url, results);
  }

  const timestamp = new Date().toISOString().split('T')[0];

  // Extract data per strategy
  const mobileData = validResults.find(r => r.strategy === 'mobile')?.data?.lighthouseResult;
  const desktopData = validResults.find(r => r.strategy === 'desktop')?.data?.lighthouseResult;

  const sections = [];

  sections.push(generateHeader(url, timestamp, validResults, options));
  sections.push(generateExecutiveSummary(mobileData, desktopData));
  sections.push(generateCoreWebVitals(mobileData, desktopData));

  // The heart of the report
  const prioritizedIssues = generatePrioritizedIssues(mobileData, desktopData);
  sections.push(prioritizedIssues);

  sections.push(generateEasyWins(mobileData, desktopData));
  sections.push(generateMediumFixes(mobileData, desktopData));
  sections.push(generateHardFixes(mobileData, desktopData));

  if (mobileData && desktopData) {
    sections.push(generateMobileDesktopDifferences(mobileData, desktopData));
  }

  sections.push(generateAgentTaskList(prioritizedIssues));
  sections.push(generateGuardrails());
  sections.push(generateAcceptanceCriteria(url));

  return sections.filter(Boolean).join('\n\n');
}

/* ========================================================================== */
/*                              HEADER & SUMMARY                              */
/* ========================================================================== */

function generateHeader(url, timestamp, results, options = {}) {
  const strategies = results.map(r => r.strategy).join(' + ');

  let header = `# PageSpeed Optimization Report

**URL**: ${url}  
**Generated**: ${timestamp}  
**Strategies**: ${strategies}  
**Tool**: Lighthouse Handoff (PageSpeed Insights → Agent Brief)`;

  if (options.settings && options.settings.provider) {
    const s = options.settings;
    const providerNames = {
      'mock': 'Mock Provider',
      'liquid-local': 'Liquid Local Companion',
      'ollama': 'Ollama Local',
      'openai': 'OpenAI',
      'claude': 'Claude'
    };
    
    const modelNames = {
      'mock': 'Mock Model',
      'LFM2.5-350M': 'LFM 2.5 350M',
      'LFM-1.3B': 'LFM 1.3B',
      'LFM-3B': 'LFM 3B'
    };

    const pName = providerNames[s.provider] || s.provider;
    const mName = modelNames[s.model] || s.model || 'Unknown';
    const mode = s.provider.includes('local') || s.provider === 'ollama' || s.provider === 'mock' ? 'Local' : 'Cloud';

    header += `\n**AI Provider**: ${pName}  \n**Model**: ${mName}  \n**Mode**: ${mode}`;
  }

  return header;
}

function generateExecutiveSummary(mobile, desktop) {
  const rows = [];

  const cats = ['performance', 'accessibility', 'best-practices', 'seo'];

  rows.push('| Category            | Mobile | Desktop |');
  rows.push('| ------------------- | ------ | ------- |');

  cats.forEach(cat => {
    const mScore = mobile ? Math.round((mobile.categories[cat]?.score || 0) * 100) : '—';
    const dScore = desktop ? Math.round((desktop.categories[cat]?.score || 0) * 100) : '—';
    const name = cat.charAt(0).toUpperCase() + cat.slice(1).replace('-', ' ');
    rows.push(`| ${name.padEnd(19)} | ${String(mScore).padEnd(6)} | ${String(dScore).padEnd(7)} |`);
  });

  return `## 1. Executive Summary

${rows.join('\n')}

> **Interpretation**: Scores above 90 are excellent. 50–89 need work. Below 50 are critical.`;
}

/* ========================================================================== */
/*                            CORE WEB VITALS                                 */
/* ========================================================================== */

function generateCoreWebVitals(mobile, desktop) {
  const lines = ['## 2. Core Web Vitals'];

  const vitals = [
    { id: 'largest-contentful-paint', label: 'LCP (Largest Contentful Paint)' },
    { id: 'cumulative-layout-shift', label: 'CLS (Cumulative Layout Shift)' },
    { id: 'interaction-to-next-paint', label: 'INP (Interaction to Next Paint)' },
    { id: 'first-contentful-paint', label: 'FCP (First Contentful Paint)' },
    { id: 'total-blocking-time', label: 'TBT (Total Blocking Time)' },
  ];

  let hasData = false;

  vitals.forEach(({ id, label }) => {
    const m = mobile?.audits?.[id];
    const d = desktop?.audits?.[id];

    if (!m && !d) return;
    hasData = true;

    const mVal = m ? formatMetricValue(m) : '—';
    const dVal = d ? formatMetricValue(d) : '—';
    const mScore = m ? getScoreEmoji(m.score) : '';
    const dScore = d ? getScoreEmoji(d.score) : '';

    lines.push(`- **${label}**: Mobile ${mVal} ${mScore} | Desktop ${dVal} ${dScore}`);
  });

  if (!hasData) {
    return null;
  }

  lines.push('\n> **Target thresholds**: LCP < 2.5s, CLS < 0.1, INP < 200ms for good scores.');
  return lines.join('\n');
}

function formatMetricValue(audit) {
  if (!audit) return '—';
  const val = audit.numericValue;

  if (audit.id.includes('paint') || audit.id.includes('blocking')) {
    return val ? `${(val / 1000).toFixed(2)}s` : '—';
  }
  if (audit.id.includes('shift')) {
    return val != null ? val.toFixed(3) : '—';
  }
  return val != null ? String(Math.round(val)) : '—';
}

function getScoreEmoji(score) {
  if (score == null) return '';
  if (score >= 0.9) return '🟢';
  if (score >= 0.5) return '🟡';
  return '🔴';
}

/* ========================================================================== */
/*                     SMART PRIORITIZED ISSUE EXTRACTION                     */
/* ========================================================================== */

/**
 * This is the most important function. We intelligently rank issues instead of dumping Lighthouse's order.
 */
function generatePrioritizedIssues(mobile, desktop) {
  const allAudits = [];

  // Collect audits from both strategies with context
  if (mobile) {
    collectAudits(mobile, 'mobile', allAudits);
  }
  if (desktop) {
    collectAudits(desktop, 'desktop', allAudits);
  }

  // Deduplicate + merge impact across strategies
  const merged = mergeAuditsById(allAudits);

  // Score and rank - be more aggressive to keep the report focused and high-signal
  const ranked = merged
    .map(audit => ({
      ...audit,
      impactScore: calculateImpactScore(audit),
    }))
    .sort((a, b) => b.impactScore - a.impactScore)
    .filter(a => a.impactScore > 30)   // Only show reasonably meaningful issues
    .slice(0, 8);                       // Hard cap at 8 items for readability

  if (ranked.length === 0) {
    return '## 3. Priority Fixes\n\nNo significant issues detected. Excellent work!';
  }

  const lines = ['## 3. Highest-Impact Issues (Ranked)'];

  lines.push('> This section is designed to be self-contained. You should not need the original Lighthouse report to act on these items.');

  // Add a short, scannable list of the most important items so an agent has immediate direction
  const topThree = ranked.slice(0, 3).map((a, i) => `${i + 1}. ${a.title}`).join('\n');
  lines.push(`**Top Priorities to Address First:**\n${topThree}\n`);

  ranked.forEach((audit, index) => {
    lines.push(formatAuditAsIssue(audit, index + 1));
  });

  return lines.join('\n\n');
}

function collectAudits(lhr, strategy, out) {
  const audits = lhr.audits;
  const categoryWeights = getCategoryWeights(lhr);

  Object.keys(audits).forEach(id => {
    const audit = audits[id];
    if (!audit || audit.score === 1 || audit.score == null) return; // Only failed/passing-with-warnings

    // Only include audits that appear in categories we care about
    const weight = categoryWeights[id] || 0;
    if (weight === 0) return;

    out.push({
      id,
      title: audit.title,
      description: audit.description,
      score: audit.score,
      numericValue: audit.numericValue,
      displayValue: audit.displayValue,
      details: audit.details,
      strategy,
      weight,
      category: getPrimaryCategory(audit, lhr),
    });
  });
}

function getCategoryWeights(lhr) {
  const weights = {};
  Object.values(lhr.categories).forEach(cat => {
    cat.auditRefs?.forEach(ref => {
      weights[ref.id] = (weights[ref.id] || 0) + (ref.weight || 1);
    });
  });
  return weights;
}

function getPrimaryCategory(audit, lhr) {
  for (const [catId, cat] of Object.entries(lhr.categories)) {
    if (cat.auditRefs?.some(r => r.id === audit.id)) {
      return catId;
    }
  }
  return 'other';
}

/**
 * Custom impact scoring — this is where we differentiate from raw Lighthouse.
 */
function calculateImpactScore(audit) {
  let score = 0;

  // Base penalty from Lighthouse score (0–1)
  const failurePenalty = (1 - (audit.score || 0)) * 100;
  score += failurePenalty * 0.6;

  // Performance impact (time savings)
  if (audit.numericValue && audit.category === 'performance') {
    if (audit.id.includes('unused-javascript') || audit.id.includes('render-blocking')) {
      score += Math.min(audit.numericValue / 200, 80); // Heavy weight on JS waste
    } else {
      score += Math.min(audit.numericValue / 500, 40);
    }
  }

  // Layout shift is particularly painful
  if (audit.id === 'cumulative-layout-shift' && audit.numericValue) {
    score += audit.numericValue * 1200;
  }

  // Large image savings
  if (audit.details?.overallSavingsBytes) {
    score += Math.min(audit.details.overallSavingsBytes / 30000, 35);
  }

  // Boost audits where we have concrete details (more actionable/self-contained for the agent)
  if (audit.details && Array.isArray(audit.details.items) && audit.details.items.length > 0) {
    score += 20;
  }

  // Cross-strategy boost
  if (audit.strategy === 'mobile') score += 15; // Mobile hurts more

  return Math.round(score);
}

function mergeAuditsById(audits) {
  const map = new Map();

  audits.forEach(a => {
    if (!map.has(a.id)) {
      map.set(a.id, { ...a, affectedStrategies: [a.strategy] });
    } else {
      const existing = map.get(a.id);
      existing.affectedStrategies.push(a.strategy);
      // Take the worse score
      if ((a.score || 1) < (existing.score || 1)) {
        existing.score = a.score;
        existing.numericValue = a.numericValue;
        existing.displayValue = a.displayValue;
      }
    }
  });

  return Array.from(map.values());
}

/* ========================================================================== */
/*                           AUDIT FORMATTING                                 */
/* ========================================================================== */

function formatAuditAsIssue(audit, rank) {
  const lines = [];

  const impact = audit.impactScore > 120 ? 'Critical' :
                 audit.impactScore > 70 ? 'High' :
                 audit.impactScore > 35 ? 'Medium' : 'Low';

  lines.push(`### ${rank}. ${audit.title}`);
  lines.push(`**Impact**: ${impact} (${audit.impactScore}) | **Category**: ${audit.category} | **Affected**: ${audit.affectedStrategies.join(' + ')}`);

  if (audit.displayValue) {
    lines.push(`**Measured**: ${audit.displayValue}`);
  }

  // Add concrete details from the audit if present (this makes the report self-contained)
  const detailsSummary = getDetailsSummary(audit);
  if (detailsSummary) {
    lines.push(detailsSummary);
  }

  // Problem statement
  lines.push(`\n**Problem**: ${stripHtml(audit.description || 'No description available.')}`);

  // Specific recommendations based on audit type
  lines.push(`\n**Recommended Fix**:`);
  lines.push(getSpecificRecommendations(audit));

  // Agent instructions
  lines.push(`\n**Coding Agent Instructions**:\n${getAgentInstructions(audit)}`);

  return lines.join('\n');
}

function getSpecificRecommendations(audit) {
  const id = audit.id;

  // We have dedicated self-contained advice for the following common audits (and more via the details extractor + improved fallback):
  // unused-javascript, render-blocking*, largest-contentful-paint / lcp*, image* / modern-image* / offscreen / lazy, font*, forced-reflow, network-dependency* / critical-request*, lcp*discovery / request, speed-index, interactive / time-to-interactive, unminified*, unused-css, text-compression / gzip / brotli, long-cache-ttl, third-party*, dom-size, server-response-time / ttfb, redirect, layout-shift* / cls, label-content-name-mismatch / accessible-names, and others via table/details.
  // For rare audits that still hit last-resort, the code comments direct to sideline (remove from high-impact or move to Other findings) and ask for guidance rather than shipping vague text. The details extractor + "Concrete items" section now provides context even for many of them.

  if (id.includes('unused-javascript')) {
    return `- Identify the largest JavaScript files that are downloaded but never used on the initial page load.\n- Remove dead code, or split it out using dynamic imports so it only loads when needed.\n- Pay special attention to third-party scripts (analytics, chat widgets, ads, etc.) — many can be loaded after the critical rendering path or on user interaction.`;
  }
  if (id.includes('render-blocking')) {
    return `- Defer non-critical CSS and JavaScript.\n- Inline critical CSS for above-the-fold content.\n- Use \`media="print"\` + JS onload trick for non-critical stylesheets.`;
  }
  if (id === 'largest-contentful-paint') {
    return `- Optimize or preload the LCP image/element.\n- Reduce server response time (TTFB).\n- Eliminate render-blocking resources affecting the LCP element.`;
  }
  if (id === 'cumulative-layout-shift') {
    return `- Set explicit width/height on images and embeds.\n- Reserve space for dynamic content (ads, embeds, web fonts).\n- Move non-critical DOM mutations out of the critical rendering path.`;
  }
  if (id.includes('image')) {
    return `- Convert large images to modern formats (WebP or AVIF) with appropriate quality settings.\n- Use responsive images (\`srcset\` + \`sizes\`) so smaller devices don't download oversized images.\n- Lazy load images that are below the fold.\n- Consider using a proper image CDN or optimization service for future images.`;
  }
  if (id.includes('font')) {
    return `- Use \`font-display: swap\` or \`optional\`.\n- Preload key web fonts.\n- Subset fonts to only the characters you need.`;
  }

  // More specific guidance for audits that previously fell back to "follow Lighthouse"
  if (id === 'forced-reflow' || id.includes('reflow')) {
    return `- Find JavaScript that reads layout properties (e.g. offsetWidth, clientHeight, getComputedStyle) right after modifying the DOM.\n- Batch all reads before writes.\n- Prefer CSS transforms and opacity for animations instead of properties that trigger layout.`;
  }

  if (id.includes('network-dependency') || id.includes('critical-request')) {
    return `- Use the listed resources/chains (or the DevTools waterfall) to map every hop in the critical path.\n- For early resources in the chain: inline small critical CSS/JS, add preload/preconnect, or move non-critical later.\n- Shorten the chain length wherever possible.`;
  }

  if (id.includes('lcp') && (id.includes('discovery') || id.includes('request'))) {
    return `- Use the LCP element identification (from DevTools or the items above) to ensure its resource is referenced directly and early in the initial HTML.\n- Avoid lazy-loading the actual LCP resource.\n- Add <link rel="preload"> for the LCP image/font if it is not discovered early enough.`;
  }

  if (id.includes('speed-index')) {
    return `- Focus on improving the visual completeness of the page as early as possible.\n- Prioritize loading above-the-fold content faster (images, fonts, critical CSS/JS).`;
  }

  if (id.includes('interactive') || id === 'time-to-interactive') {
    return `- Reduce long JavaScript execution on the main thread.\n- Break up long tasks.\n- Defer non-critical JavaScript so the page becomes interactive sooner.`;
  }

  if (id.includes('unminified-javascript')) {
    return `- Minify the JavaScript files listed in the audit details (use terser, esbuild, or your bundler's minify option).\n- This is usually a quick win with no functional risk.`;
  }

  if (id.includes('unminified-css')) {
    return `- Minify the CSS files listed.\n- Most bundlers (Vite, Webpack, etc.) have built-in CSS minification — enable it.`;
  }

  if (id.includes('unused-css')) {
    return `- Remove or scope unused CSS rules (use PurgeCSS, UnCSS, or your framework's built-in tree-shaking for CSS).\n- For critical CSS, extract only the above-the-fold styles and inline them.`;
  }

  if (id.includes('modern-image-formats') || id.includes('webp') || id.includes('avif')) {
    return `- Convert the images flagged to WebP (and AVIF where supported) using sharp, imagemin, or your image hosting service.\n- Provide fallbacks for older browsers using <picture> or server content negotiation.`;
  }

  if (id.includes('offscreen-images') || id.includes('lazy-load')) {
    return `- Add native \`loading="lazy"\` to images and iframes that are below the initial viewport.\n- For critical above-the-fold images, remove lazy loading and consider preloading the LCP one.`;
  }

  if (id.includes('text-compression') || id.includes('gzip') || id.includes('brotli')) {
    return `- Enable gzip or (preferably) Brotli compression on your server/CDN for text assets (HTML, CSS, JS, SVG).\n- This is usually a one-line server config change or CDN setting.`;
  }

  if (id.includes('long-cache-ttl') || id.includes('cache')) {
    return `- Set long Cache-Control / immutable headers (e.g. 1 year) on static assets that have content hashes in their filenames.\n- For HTML, use short TTL or no-cache so updates are picked up.`;
  }

  if (id.includes('third-party')) {
    return `- Audit the third-party scripts flagged.\n- Consider using facades (e.g. lite-youtube, loading on interaction) for heavy embeds like YouTube, Twitter, chat widgets.\n- Load non-essential third parties after the main content or on user interaction.`;
  }

  if (id.includes('dom-size')) {
    return `- Reduce the total number of DOM nodes (aim for < 1500 total, < 60 depth).\n- Look for repeated elements that can be virtualized, paginated, or rendered on demand (e.g. long lists, large tables).`;
  }

  if (id.includes('server-response-time') || id.includes('ttfb')) {
    return `- Investigate and improve Time to First Byte (server response time).\n- Common fixes: enable caching, use a CDN, optimize database queries, move to edge functions / serverless closer to users.`;
  }

  if (id.includes('redirect')) {
    return `- Eliminate unnecessary redirects, especially in the critical path.\n- Update links and resources to point directly to the final URL.`;
  }

  if (id.includes('layout-shift') || id === 'layout-shift-elements') {
    return `- Find elements causing CLS (usually images without dimensions, dynamically injected content, web fonts, or ads).\n- Set explicit width/height or aspect-ratio on media.\n- Reserve space for late-loading content.`;
  }

  if (id.includes('label-content-name-mismatch') || id.includes('accessible-names')) {
    return `- For each flagged element, ensure the accessible name (used by screen readers) matches or clearly describes the visible text the user sees.\n- Best: make the visible text itself the accessible name (e.g. don't hide text with aria-hidden on the label).\n- If needed, add aria-label that matches the visible content exactly.\n- Test with a screen reader or accessibility tree in DevTools after changes.`;
  }

  // Generic fallback - make it as self-contained as possible. This should rarely be hit now.
  if (audit.details?.type === 'table' && audit.details?.items?.length) {
    const count = audit.details.items.length;
    return `- Review the ${count} specific item(s) flagged in this audit (see "Concrete items from the audit" section above for details extracted from the report).
- Common causes for this audit include inefficient resource loading, missing attributes (e.g. width/height, aria-label), or unnecessary DOM operations after style changes. Examine the page and the listed items for the exact issues described in the Problem section.
- Use DevTools Elements/Accessibility or Performance tabs to inspect the flagged resources/elements directly.`;
  }

  // Last resort - still try to give the agent something concrete to do
  // If you encounter an audit that keeps landing here and has poor value (e.g. the items are not useful), sideline it (remove from high-impact list or move to "Other findings") and ask the user for guidance on what specific advice to give for that audit ID.
  return `- Open DevTools and use the relevant panel for this issue (Lighthouse, Performance, Network, or Accessibility tree).
- Use any "Concrete items from the audit" listed above as your direct targets (or expand the full details in DevTools if the summary is limited).
- Implement the smallest targeted fix that addresses the issue described in the Problem section while preserving layout, branding, and all existing functionality.
- Re-run this report (same URL + strategies) to verify improvement.`;
}

function getAgentInstructions(audit) {
  const id = audit.id;

  // Strong, self-contained instructions for high-frequency audits
  if (id.includes('unused-javascript')) {
    return `1. Open the page and use the Network + Coverage tab in DevTools to identify JavaScript that is downloaded but never executed on initial load.
2. Prioritize the largest unused bundles.
3. Either remove the code entirely, code-split it with dynamic imports, or defer it if it is not needed for the initial render or critical user interactions.
4. Do **not** remove scripts that power key functionality (forms, navigation, tracking that must fire early).`;
  }

  if (id.includes('render-blocking')) {
    return `1. Identify the resources (CSS/JS) that are blocking the initial render.
2. For non-critical CSS: defer it or load it asynchronously.
3. For critical CSS: inline the above-the-fold styles directly in the <head>.
4. For non-critical JavaScript: add defer or async attributes, or move it to the bottom of the body.`;
  }

  if (id === 'largest-contentful-paint' || id.includes('lcp')) {
    return `1. Identify what element is the Largest Contentful Paint (check mobile first, as it is usually worse).
2. Ensure the LCP image or text is loaded as early as possible (preload the resource if it's an image or font).
3. Reduce server response time (TTFB) if it's high.
4. Eliminate or defer any render-blocking resources that delay the LCP element.`;
  }

  if (id === 'forced-reflow' || id.includes('reflow')) {
    return `1. Search the JavaScript for places where you read layout properties (offsetWidth, offsetHeight, getBoundingClientRect, etc.) immediately after writing to the DOM.
2. Batch DOM reads and writes. Read all layout values first, then perform writes.
3. Consider using CSS containment or transform/opacity animations instead of layout-triggering properties.`;
  }

  if (id.includes('network-dependency') || id.includes('critical-request')) {
    return `1. Open DevTools Network tab, reload the page, and look at the "Waterfall" or use the "Critical request chains" view (or Lighthouse report details) to see the chain of blocking requests.
2. For each link in the chain: inline small critical resources, add <link rel="preload"> or <link rel="preconnect"> for key domains, or defer non-critical ones.
3. Reduce the number of hops by combining files or using HTTP/2 multiplexing where possible.`;
  }

  if (id.includes('lcp') && (id.includes('discovery') || id.includes('request'))) {
    return `1. Use DevTools Performance or Lighthouse to identify the exact LCP element (usually an image or large text block).
2. Ensure that element's resource (img src, background, font, etc.) is referenced directly in the initial HTML (no lazy loading on LCP, no JS injection for it).
3. Add <link rel="preload" as="image" href="..."> (or for font) early in <head> if the resource is discovered late.
4. Move any blocking CSS/JS that delays the LCP element.`;
  }

  if (id.includes('unminified')) {
    return `Minification is almost always safe for production builds — just ensure your build process is correctly configured and source maps are available for debugging if you need to debug production code.`;
  }

  if (id.includes('unused-css') || id.includes('offscreen-images')) {
    return `These are usually safe mechanical changes. After implementing, visually verify on mobile and test any interactive elements that might be affected by lazy loading.`;
  }

  if (id.includes('third-party')) {
    return `Be careful with third-party scripts that provide visible UI or critical tracking/conversion pixels — test the affected flows thoroughly after changes. Prefer loading on interaction (facades) where possible.`;
  }

  if (id.includes('dom-size')) {
    return `This often requires structural changes (virtual lists, pagination, lazy rendering of sections, or switching to a more efficient UI library for that part of the page). Make changes incrementally and test user flows after each significant reduction.`;
  }

  if (id.includes('server-response-time') || id.includes('ttfb')) {
    return `This may require backend / hosting / CDN changes. Common wins include enabling Brotli, adding edge caching, moving compute closer to users (edge functions), or optimizing slow database/API calls on the critical path. Coordinate if you're not the one controlling the backend.`;
  }

  if (id.includes('image') || id.includes('webp') || id.includes('avif') || id.includes('offscreen') || id.includes('lazy')) {
    return `Use the specific image URLs in the "Concrete items" list above. For each: convert to WebP/AVIF (with fallbacks), add proper srcset/sizes, and lazy-load only those below the fold. For the LCP image, ensure it is not lazy-loaded and is preloaded if possible.`;
  }

  if (id.includes('label-content-name-mismatch') || id.includes('accessible-names')) {
    return `For the specific element(s) listed in "Concrete items" (e.g. the text "LEMONTEED FM..."), make the accessible name match the visible text exactly. Use the same text content for the label, or set aria-label to the visible text. Avoid aria-hidden on visible labels. Test the accessibility tree in DevTools.`;
  }

  // Default instruction - still tries to be actionable. This is the safety net for rare audits.
  return `Open the relevant DevTools panel (Performance, Network, Elements, or Accessibility) and locate the exact resources or elements from the "Concrete items from the audit" list above (or expand the audit details in DevTools).
Implement the smallest targeted change that addresses the issue described in the Problem section while preserving layout, branding, and functionality.
Re-run this report after the change to measure impact.`;
}

function stripHtml(str) {
  return str.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
}

/**
 * Extracts concrete, actionable details from the audit.details when available.
 * This is critical for making the report self-contained.
 * We surface the top offending resources (scripts, images, etc.) with their impact.
 */
function getDetailsSummary(audit) {
  const details = audit.details;
  const id = audit.id || '';

  // Special handling for audits with non-standard details shape (e.g. critical request chains)
  if (id.includes('network-dependency') || id.includes('critical-request')) {
    if (details && details.chains) {
      // Summarize the root of the chain(s)
      let summary = '\n\n**Critical request chain summary (top level):**\n';
      const roots = Object.keys(details.chains).slice(0, 4);
      roots.forEach((root, i) => {
        const chain = details.chains[root];
        const url = chain.url || root;
        const short = url.length > 100 ? url.substring(0,97)+'...' : url;
        summary += `${i+1}. ${short}`;
        if (chain.transferSize) summary += ` (~${Math.round(chain.transferSize/1024)} KiB)`;
        summary += '\n';
      });
      if (Object.keys(details.chains).length > roots.length) {
        summary += `... and more in the chain.\n`;
      }
      return summary + 'Use DevTools Network > Waterfall or the full chain details to see every hop and timing.';
    } else if (details && Array.isArray(details.items) && details.items.length > 0) {
      // Fallback for when network audit uses items with sections/titles (e.g. preconnect lists)
      let summary = '\n\n**Network dependency sections (from audit):**\n';
      details.items.slice(0, 5).forEach((item, i) => {
        if (item.title) {
          summary += `${i+1}. ${item.title}\n`;
        } else if (item.type) {
          summary += `${i+1}. ${item.type}\n`;
        }
      });
      return summary + 'These often indicate preconnect or early resource opportunities. Use DevTools Network > Waterfall (filter by "Preconnect" or look at initiator chain) to see the actual request URLs and timings for the full dependency tree.';
    }
  }

  // Special for forced-reflow audits (often table of scripts causing reflows)
  if (id === 'forced-reflow' || id.includes('reflow')) {
    if (details && Array.isArray(details.items) && details.items.length > 0) {
      let summary = '\n\n**Forced reflow sources (from audit):**\n';
      details.items.slice(0, 5).forEach((item, i) => {
        if (item.scriptUrl) {
          summary += `${i+1}. Script: ${item.scriptUrl}\n`;
        } else if (item.url) {
          summary += `${i+1}. ${item.url}\n`;
        } else if (item.type || item.title) {
          summary += `${i+1}. ${item.type || item.title}\n`;
        }
      });
      return summary + 'Use DevTools Performance > Bottom-up, select "Forced reflow" or long tasks, to see the exact stack and script causing the layout thrashing.';
    }
  }

  if (!details || !Array.isArray(details.items) || details.items.length === 0) {
    return '';
  }

  const items = details.items.slice(0, 6); // top 6 most impactful
  let summary = '\n\n**Concrete items from the audit (top offenders):**\n';

  items.forEach((item, idx) => {
    let line = `${idx + 1}. `;
    let labelFound = false;

    // Try common URL-like fields
    const urlFields = ['url', 'source', 'src', 'request', 'location', 'scriptUrl', 'sourceURL', 'stackTrace'];
    for (const f of urlFields) {
      if (item[f] && typeof item[f] === 'string' && item[f].length > 3) {
        const short = item[f].length > 120 ? item[f].substring(0, 117) + '...' : item[f];
        line += short;
        labelFound = true;
        break;
      } else if (item[f] && typeof item[f] === 'object' && item[f].url) {
        // handle nested like stackTrace.url or similar
        const short = item[f].url.length > 120 ? item[f].url.substring(0, 117) + '...' : item[f].url;
        line += short;
        labelFound = true;
        break;
      }
    }

    if (!labelFound) {
      if (item.node && item.node.nodeLabel) {
        line += `Element: ${item.node.nodeLabel}`;
        labelFound = true;
      } else if (item.label) {
        line += item.label;
        labelFound = true;
      } else if (item.name) {
        line += item.name;
        labelFound = true;
      } else if (item.title) {
        line += item.title;
        labelFound = true;
      } else if (item.path) {
        line += `DOM path: ${item.path}`;
        labelFound = true;
      }
    }

    // For network-dependency audits, label list-sections/titles clearly and note to check DevTools for URLs
    if (!labelFound && (id.includes('network-dependency') || id.includes('critical-request')) && item.title) {
      line += `Network section: ${item.title}`;
      labelFound = true;
    }

    if (!labelFound) {
      // Deep search for any URL-like or resource string in the item (handles nested or odd shapes)
      for (const [k, v] of Object.entries(item)) {
        if (typeof v === 'string' && (v.includes('http') || v.includes('.js') || v.includes('.css') || v.includes('.webp') || v.includes('.png') || v.includes('.jpg') || v.includes('.jpeg') || v.length > 8)) {
          const short = v.length > 80 ? v.substring(0, 77) + '...' : v;
          line += `${k}: ${short}`;
          labelFound = true;
          break;
        }
        // Check nested objects for url
        if (typeof v === 'object' && v !== null) {
          for (const [nk, nv] of Object.entries(v)) {
            if (typeof nv === 'string' && (nv.includes('http') || nk.toLowerCase().includes('url'))) {
              const short = nv.length > 80 ? nv.substring(0, 77) + '...' : nv;
              line += `${k}.${nk}: ${short}`;
              labelFound = true;
              break;
            }
          }
          if (labelFound) break;
        }
      }
    }

    if (!labelFound) {
      // Try to surface something from the item object, prioritize any url-like key
      let urlLike = null;
      for (const [k, v] of Object.entries(item)) {
        if (typeof v === 'string' && (k.toLowerCase().includes('url') || k.toLowerCase().includes('src') || k.toLowerCase().includes('source'))) {
          urlLike = `${k}: ${v.length > 60 ? v.substring(0,57)+'...' : v}`;
          break;
        }
      }
      if (urlLike) {
        line += urlLike;
      } else {
        const keysWithValues = Object.entries(item)
          .filter(([k,v]) => v != null && typeof v !== 'object')
          .slice(0, 2)
          .map(([k,v]) => `${k}=${String(v).substring(0,40)}`)
          .join(', ');
        if (keysWithValues) {
          line += `Item (${keysWithValues})`;
        } else if (item.type === 'table' || item.type === 'list-section' || item.type === 'checklist') {
          line += `Table/list item from audit details (expand in DevTools for the actual resources or selectors)`;
        } else {
          line += 'Resource (inspect full item in DevTools Lighthouse or Performance for exact URL/selector)';
        }
      }
    }

    // Attach impact numbers if present
    if (typeof item.wastedBytes === 'number' && item.wastedBytes > 0) {
      const kb = Math.round(item.wastedBytes / 1024);
      line += ` — wasted ~${kb} KiB`;
    }
    if (typeof item.wastedMs === 'number' && item.wastedMs > 0) {
      line += ` — potential savings ~${Math.round(item.wastedMs)}ms`;
    }
    if (item.totalBytes && !item.wastedBytes) {
      const kb = Math.round(item.totalBytes / 1024);
      line += ` — ${kb} KiB`;
    }

    summary += line + '\n';
  });

  if (details.items.length > items.length) {
    summary += `... and ${details.items.length - items.length} more item(s).\n`;
  }

  return summary;
}

/* ========================================================================== */
/*                    EASY / MEDIUM / HARD CATEGORIZATION                     */
/* ========================================================================== */

function generateEasyWins(mobile, desktop) {
  // These are general quick checks. The main high-impact work is in section 3.
  // We keep this lightweight on purpose.
  return `## 4. Easy Wins (Quick Improvements)

These are typically low-risk and fast to implement (often < 1 hour). The highest value work is listed in section 3 above.

- Ensure all informative images have meaningful \`alt\` text (Accessibility)
- Add or improve meta description and title tags if missing or weak (SEO)
- Enable text compression (gzip or Brotli) on the server for HTML/CSS/JS/SVG
- Set long Cache-Control headers (with content hashes in filenames) for static assets
- Use \`font-display: swap\` (or better) for web fonts to avoid invisible text

Run this report again after these changes.`;
}

function generateMediumFixes(mobile, desktop) {
  return `## 5. Medium Fixes

These usually take more effort or coordination but have good ROI. Prioritize based on the specific items in section 3.

- JavaScript code-splitting + defer non-critical bundles (focus on the largest unused ones first)
- Proper image optimization pipeline (modern formats + responsive images + lazy loading below the fold)
- Eliminate or defer render-blocking CSS/JS for above-the-fold content
- Reduce impact of heavy third-party scripts (facades, delay until interaction, or remove)
- Improve caching strategy and CDN usage`;
}

function generateHardFixes(mobile, desktop) {
  return `## 6. Hard / Architectural Fixes

These are higher effort and should only be considered after the concrete items in section 3 are addressed and re-tested.

- Significant changes to JavaScript architecture (e.g. islands architecture, partial hydration, moving heavy libs to web workers)
- Adopting or improving SSR / edge rendering / streaming for the critical path
- Large-scale replacement or removal of heavy third-party dependencies
- Major overhaul of image pipeline / hosting (e.g. dedicated image CDN with on-the-fly optimization)
- Fundamental changes to how the critical rendering path is built (e.g. new bundling strategy, different framework rendering model)`;
}

/* ========================================================================== */
/*                        MOBILE VS DESKTOP COMPARISON                        */
/* ========================================================================== */

function generateMobileDesktopDifferences(mobile, desktop) {
  const mPerf = Math.round((mobile.categories.performance?.score || 0) * 100);
  const dPerf = Math.round((desktop.categories.performance?.score || 0) * 100);

  const diff = Math.abs(mPerf - dPerf);

  let analysis = 'Performance is relatively consistent across devices.';
  if (diff > 25) {
    analysis = mPerf < dPerf
      ? 'Mobile performance is significantly worse than desktop. Focus on JavaScript reduction, image optimization, and server response time for mobile.'
      : 'Desktop performance is unusually worse — unusual. Investigate heavy desktop-only scripts or rendering differences.';
  }

  return `## 7. Mobile vs Desktop Differences

- **Mobile Performance**: ${mPerf}
- **Desktop Performance**: ${dPerf}
- **Gap**: ${diff} points

${analysis}`;
}

/* ========================================================================== */
/*                           AGENT TASK LIST                                  */
/* ========================================================================== */

function generateAgentTaskList(prioritizedSection) {
  return `## 8. Agent Task List (Prioritized Order)

1. Start with the "Top Priorities to Address First" listed at the top of section 3. Use the "Concrete items from the audit" details for each one as your direct targets.
2. Focus on Performance issues first (they usually move the needle most for user experience and SEO).
3. Make one significant change at a time, then re-run this tool on the same URL/strategy to measure impact.
4. Never remove or heavily modify scripts, third-party code, or critical rendering paths without verifying the functionality still works (especially forms, navigation, and conversion tracking).
5. After your changes, re-generate the report with the exact same settings and compare.`;
}

/* ========================================================================== */
/*                              GUARDRAILS                                    */
/* ========================================================================== */

function generateGuardrails() {
  return `## 9. Guardrails — Do NOT Do These Things

**Strict rules for any coding agent working on this site:**

- Do **not** redesign layouts, spacing, or visual hierarchy.
- Do **not** change brand colors, typography, or existing UI components unless explicitly required by a performance fix.
- Do **not** remove or disable analytics, tracking, or marketing scripts without explicit approval.
- Do **not** introduce new third-party dependencies or frameworks.
- Do **not** refactor large parts of the application "while you're in there".
- Do **not** change server configuration or deployment pipelines unless the audit specifically calls for it.
- Preserve all existing functionality and interactive behavior.
- When in doubt, make the smallest possible change that improves the metric.

These guardrails exist because aggressive "optimization" frequently destroys business value and user experience.`;
}

/* ========================================================================== */
/*                        ACCEPTANCE + RETEST                                 */
/* ========================================================================== */

function generateAcceptanceCriteria(url) {
  return `## 10. Acceptance Criteria & Retest Instructions

**After making changes:**

1. Re-run this extension on the same URL using the exact same settings.
2. The targeted audit(s) should show meaningful improvement (ideally move from red → yellow or yellow → green).
3. No new console errors should appear.
4. All critical user flows (forms, navigation, checkout, etc.) must continue to work exactly as before.
5. Core Web Vitals should not regress on the pages you modified.

**Retest command**: Use Lighthouse Handoff again with the same Mobile + Desktop + Performance settings and compare the new report against this one.

---

*Report generated by Lighthouse Handoff — turning raw PageSpeed data into actionable, safe implementation briefs for coding agents.*`;
}

/* ========================================================================== */
/*                              ERROR REPORT                                  */
/* ========================================================================== */

function generateErrorReport(url, results) {
  const errors = results
    .filter(r => !r.success)
    .map(r => `- **${r.strategy}**: ${r.error || 'Unknown error'}`)
    .join('\n');

  return `# PageSpeed Optimization Report — Error

**URL**: ${url}

The audit could not be completed successfully.

### Errors Encountered

${errors || 'No detailed error information available.'}

### Next Steps

1. Verify your Google PageSpeed Insights API key is valid and has quota.
2. Check that the URL is publicly accessible (no auth walls, no local dev servers).
3. Try running with only the Performance category first.
4. If rate limited (429), wait 60–120 seconds and retry.

If the problem persists, open an issue with the raw error details from the console.`;
}
