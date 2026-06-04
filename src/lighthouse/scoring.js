// src/lighthouse/scoring.js
// Issue Priority Engine — ranks Opportunities by business / performance impact.
// Operates on LighthouseSummary.opportunities in-place and also exposes helpers
// for callers that want RankedIssue objects.

/** @typedef {import('../ai/types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../ai/types.js').RankedIssue} RankedIssue */
/** @typedef {import('../ai/types.js').Priority} Priority */
/** @typedef {import('../ai/types.js').Opportunity} Opportunity */

// ---------------------------------------------------------------------------
// Metric threshold rules (Task 2)
// ---------------------------------------------------------------------------

const METRIC_RULES = [
  // LCP > 4s → Critical
  { check: (summary) => summary.metrics.lcp > 4000,   id: '__lcp_critical', title: 'LCP > 4s (Critical)', priority: 'critical', impactScore: 150 },
  // LCP 2.5–4s → High
  { check: (summary) => summary.metrics.lcp > 2500,   id: '__lcp_high',     title: 'LCP > 2.5s (Needs Improvement)', priority: 'high', impactScore: 90 },
  // CLS > 0.25 → Critical
  { check: (summary) => summary.metrics.cls > 0.25,   id: '__cls_critical', title: 'CLS > 0.25 (Critical)', priority: 'critical', impactScore: 140 },
  // CLS 0.1–0.25 → High
  { check: (summary) => summary.metrics.cls > 0.1,    id: '__cls_high',     title: 'CLS > 0.1 (Needs Improvement)', priority: 'high', impactScore: 85 },
  // INP > 500ms → Critical
  { check: (summary) => summary.metrics.inp > 500,    id: '__inp_critical', title: 'INP > 500ms (Critical)', priority: 'critical', impactScore: 130 },
  // TBT > 600ms → High
  { check: (summary) => summary.metrics.tbt > 600,    id: '__tbt_high',     title: 'TBT > 600ms (High)', priority: 'high', impactScore: 80 },
];

// Audit-id pattern → priority override
const AUDIT_PRIORITY_MAP = /** @type {Array<{pattern: RegExp, priority: Priority, boost: number}>} */ ([
  { pattern: /largest-contentful-paint$/,    priority: 'critical', boost: 50 },
  { pattern: /cumulative-layout-shift$/,     priority: 'critical', boost: 50 },
  { pattern: /interaction-to-next-paint$/,   priority: 'critical', boost: 40 },
  { pattern: /render-blocking/,              priority: 'high',     boost: 30 },
  { pattern: /unused-javascript/,            priority: 'high',     boost: 25 },
  { pattern: /uses-optimized-images/,        priority: 'high',     boost: 20 },
  { pattern: /modern-image-formats/,         priority: 'high',     boost: 20 },
  { pattern: /offscreen-images/,             priority: 'medium',   boost: 10 },
  { pattern: /uses-responsive-images/,       priority: 'medium',   boost: 10 },
  { pattern: /unused-css/,                   priority: 'medium',   boost: 5  },
  { pattern: /unminified/,                   priority: 'medium',   boost: 5  },
  { pattern: /text-compression/,             priority: 'medium',   boost: 5  },
  { pattern: /long-cache-ttl/,               priority: 'low',      boost: 0  },
  { pattern: /dom-size/,                     priority: 'medium',   boost: 5  },
  { pattern: /server-response-time/,         priority: 'high',     boost: 15 },
]);

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Score and rank all opportunities in a LighthouseSummary.
 * Mutates priority + impactScore on each opportunity and returns a sorted list.
 *
 * @param {LighthouseSummary} summary
 * @returns {RankedIssue[]}
 */
export function rankIssues(summary) {
  const ranked = summary.opportunities.map(opp => rankOpportunity(opp, summary));
  ranked.sort((a, b) => b.impactScore - a.impactScore);
  return ranked;
}

/**
 * Augment a LighthouseSummary with metric-level synthetic issues and ranked opportunities.
 * Returns RankedIssue[] covering both metric rules and audit-based issues.
 * Synthetic metric issues are only included when no audit already covers them.
 *
 * @param {LighthouseSummary} summary
 * @returns {RankedIssue[]}
 */
export function buildRankedIssueList(summary) {
  // Score all audit-based opportunities first
  const auditIssues = rankIssues(summary);

  // Filter to only meaningfully failing audits (score < 0.9)
  const significantAuditIssues = auditIssues.filter(i => {
    const opp = summary.opportunities.find(o => o.id === i.id);
    return !opp || (opp._score ?? 1) < 0.9;
  });

  // Build a set of audit IDs that are already well-covered
  const coveredPatterns = {
    lcp: significantAuditIssues.some(i => i.id.includes('largest-contentful-paint') || i.id.includes('lcp')),
    cls: significantAuditIssues.some(i => i.id.includes('cumulative-layout-shift') || i.id.includes('layout-shift')),
    inp: significantAuditIssues.some(i => i.id.includes('interaction-to-next-paint') || i.id.includes('inp')),
    tbt: significantAuditIssues.some(i => i.id.includes('total-blocking-time') || i.id.includes('tbt')),
  };

  // Metric threshold synthetics — only add when not already covered by an audit
  const metricIssues = buildMetricIssues(summary).filter(mi => {
    if (mi.id.includes('lcp')) return !coveredPatterns.lcp;
    if (mi.id.includes('cls')) return !coveredPatterns.cls;
    if (mi.id.includes('inp')) return !coveredPatterns.inp;
    if (mi.id.includes('tbt')) return !coveredPatterns.tbt;
    return true;
  });

  return [...metricIssues, ...significantAuditIssues]
    .sort((a, b) => b.impactScore - a.impactScore);
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * @param {Opportunity} opp
 * @param {LighthouseSummary} summary
 * @returns {RankedIssue}
 */
function rankOpportunity(opp, summary) {
  let score = 0;

  // 1. Base: failure severity (0–60)
  const failurePenalty = (1 - (opp._score || 0)) * 100;
  score += failurePenalty * 0.6;

  // 2. Audit-specific boosts
  const rule = AUDIT_PRIORITY_MAP.find(r => r.pattern.test(opp.id));
  const priority = rule?.priority ?? derivePriority(score);
  if (rule) score += rule.boost;

  // 3. Performance-category extras
  if (opp._category === 'performance') {
    if (/unused-javascript|render-blocking/.test(opp.id)) {
      score += Math.min((opp._numericValue ?? 0) / 200, 80);
    } else {
      score += Math.min((opp._numericValue ?? 0) / 500, 40);
    }
  }

  // 4. CLS compound penalty
  if (opp.id === 'cumulative-layout-shift') {
    score += (opp._numericValue ?? 0) * 1200;
  }

  // 5. Byte savings
  if (opp._overallSavingsBytes > 0) {
    score += Math.min(opp._overallSavingsBytes / 30_000, 35);
  }

  // 6. Actionability boost (has concrete items)
  if (opp._hasItems) score += 20;

  // 7. Mobile bonus (mobile perf matters more for CWV)
  if (summary.strategy === 'mobile') score += 15;

  const impactScore = Math.round(score);

  // Update the opportunity in-place so callers share the computed value
  opp.impactScore = impactScore;
  opp.priority    = rule?.priority ?? derivePriority(impactScore);

  return {
    priority,
    id:                 opp.id,
    title:              opp.title,
    impactScore,
    displayValue:       opp.displayValue,
    description:        opp.description,
    affectedStrategies: opp.affectedStrategies,
  };
}

/**
 * Convert numeric impact to a priority label.
 * Matches the thresholds used by report-builder.js for consistency.
 *
 * @param {number} score
 * @returns {Priority}
 */
function derivePriority(score) {
  if (score > 120) return 'critical';
  if (score > 70)  return 'high';
  if (score > 35)  return 'medium';
  return 'low';
}

/**
 * Build synthetic RankedIssue entries from metric threshold rules.
 *
 * @param {LighthouseSummary} summary
 * @returns {RankedIssue[]}
 */
function buildMetricIssues(summary) {
  return METRIC_RULES
    .filter(rule => rule.check(summary))
    .map(rule => ({
      priority:    rule.priority,
      id:          rule.id,
      title:       rule.title,
      impactScore: rule.impactScore,
    }));
}
