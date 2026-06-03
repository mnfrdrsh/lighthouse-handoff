// src/ai/prompts/client.js
// Client-facing prompt template.
// Explains issues in plain language, avoids technical jargon,
// and focuses on business impact rather than implementation details.

/** @typedef {import('../types.js').AIAnalysis} AIAnalysis */
/** @typedef {import('../types.js').LighthouseSummary} LighthouseSummary */

/**
 * Build a client-facing, non-technical summary from an AIAnalysis.
 *
 * @param {AIAnalysis} analysis
 * @param {LighthouseSummary} summary
 * @returns {string}
 */
export function buildClientPrompt(analysis, summary) {
  const date = new Date().toISOString().split('T')[0];

  return `# Website Performance Report

**Website**: ${summary.url}  
**Report Date**: ${date}  
**Device tested**: ${summary.strategy === 'mobile' ? '📱 Mobile' : '🖥️ Desktop'}

---

## What This Report Tells You

We tested your website's speed and user experience using Google's Lighthouse tool.
Below is a plain-language summary of what we found and what our team plans to fix.

---

## Overall Health Check

${buildClientSummary(analysis, summary)}

---

## What We Will Fix First

These issues have the biggest impact on how fast your site feels to visitors.
Each fix will be tested carefully before going live.

${analysis.priorityFixes.map((fix, i) => formatClientFix(fix, i + 1)).join('\n\n')}

---

## Quick Improvements

Smaller improvements our team can make in under a day:

${analysis.quickWins.map(w => `- ${simplify(w)}`).join('\n')}

---

## How We Will Know It Worked

After the improvements are live, we will re-test the website and check that:

${analysis.acceptanceCriteria.map(c => `- ${clientCriteria(c)}`).join('\n')}

---

*This report was produced by Lighthouse Handoff and reviewed by your development team.*
`;
}

/**
 * @param {AIAnalysis} analysis
 * @param {LighthouseSummary} summary
 * @returns {string}
 */
function buildClientSummary(analysis, summary) {
  const { scores } = summary;
  const perfLabel  = clientScoreLabel(scores.performance);
  const a11yLabel  = clientScoreLabel(scores.accessibility);

  return `| Area | Score | Status |
| ---- | ----- | ------ |
| Speed | ${scores.performance}/100 | ${perfLabel} |
| Accessibility | ${scores.accessibility}/100 | ${a11yLabel} |
| Search Engine Friendliness | ${scores.seo}/100 | ${clientScoreLabel(scores.seo)} |
| Best Practices | ${scores.bestPractices}/100 | ${clientScoreLabel(scores.bestPractices)} |

${analysis.executiveSummary.replace(/\*\*[^*]+\*\*: /g, '').replace(/\*\*([^*]+)\*\*/g, '$1')}`;
}

/**
 * @param {import('../types.js').PriorityFix} fix
 * @param {number} rank
 * @returns {string}
 */
function formatClientFix(fix, rank) {
  // Strip technical jargon from the title and reasoning for clients
  const title     = simplify(fix.title);
  const reasoning = simplify(fix.reasoning);

  return `### ${rank}. ${title}

**Why it matters**: ${reasoning}

**What we will do**: ${simplify(fix.instructions[0] ?? 'Investigate and implement the appropriate fix.')}`;
}

/**
 * Replace common technical terms with plain language equivalents.
 *
 * @param {string} text
 * @returns {string}
 */
function simplify(text) {
  return text
    .replace(/LCP/g,                 'page load speed')
    .replace(/CLS/g,                 'visual stability')
    .replace(/INP/g,                 'responsiveness')
    .replace(/TBT/g,                 'page responsiveness')
    .replace(/TTFB/g,                'server response time')
    .replace(/Core Web Vitals/g,     'Google speed metrics')
    .replace(/DevTools/g,            'developer tools')
    .replace(/Lighthouse/g,          'Google's speed test')
    .replace(/JavaScript/gi,         'website code')
    .replace(/CSS/gi,                'visual styling code')
    .replace(/CDN/g,                 'content delivery network')
    .replace(/`([^`]+)`/g,           '"$1"')
    .replace(/\*\*([^*]+)\*\*/g,     '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
}

/**
 * @param {string} criteria
 * @returns {string}
 */
function clientCriteria(criteria) {
  return simplify(criteria);
}

/**
 * @param {number} score
 * @returns {string}
 */
function clientScoreLabel(score) {
  if (score >= 90) return '✅ Excellent';
  if (score >= 50) return '⚠️ Needs Work';
  return '❌ Needs Urgent Attention';
}
