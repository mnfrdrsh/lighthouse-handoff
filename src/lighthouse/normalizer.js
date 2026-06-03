// src/lighthouse/normalizer.js
// Transforms raw PSI JSON into a clean LighthouseSummary.
// This is the boundary: raw PSI data must never go beyond this module.

/** @typedef {import('../ai/types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../ai/types.js').Strategy} Strategy */
/** @typedef {import('../ai/types.js').Opportunity} Opportunity */

/**
 * Normalize a raw PageSpeed Insights lighthouseResult for one strategy
 * into a compact LighthouseSummary ready for AI analysis.
 *
 * @param {Object} lhr - Raw lighthouseResult from PSI API
 * @param {Strategy} strategy
 * @returns {LighthouseSummary}
 */
export function normalizeLighthouseResult(lhr, strategy) {
  return {
    url: lhr.finalUrl || lhr.requestedUrl || '',
    strategy,
    scores: extractScores(lhr),
    metrics: extractMetrics(lhr),
    opportunities: extractOpportunities(lhr),
  };
}

/**
 * Normalize a full PSI results array (one entry per strategy) into LighthouseSummaries.
 *
 * @param {Array<{strategy: Strategy, success: boolean, data: Object}>} results
 * @returns {LighthouseSummary[]}
 */
export function normalizeResults(results) {
  return results
    .filter(r => r.success && r.data?.lighthouseResult)
    .map(r => normalizeLighthouseResult(r.data.lighthouseResult, r.strategy));
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * @param {Object} lhr
 * @returns {{ performance: number, accessibility: number, seo: number, bestPractices: number }}
 */
function extractScores(lhr) {
  const cats = lhr.categories || {};
  return {
    performance:     toPercent(cats['performance']?.score),
    accessibility:   toPercent(cats['accessibility']?.score),
    seo:             toPercent(cats['seo']?.score),
    bestPractices:   toPercent(cats['best-practices']?.score),
  };
}

/**
 * @param {Object} lhr
 * @returns {{ lcp: number, cls: number, inp: number, tbt: number }}
 */
function extractMetrics(lhr) {
  const audits = lhr.audits || {};
  return {
    lcp: audits['largest-contentful-paint']?.numericValue ?? 0,
    cls: audits['cumulative-layout-shift']?.numericValue ?? 0,
    inp: audits['interaction-to-next-paint']?.numericValue ?? 0,
    tbt: audits['total-blocking-time']?.numericValue ?? 0,
  };
}

/**
 * Pull all failed/warning audits that have category weights into Opportunities.
 *
 * @param {Object} lhr
 * @returns {Opportunity[]}
 */
function extractOpportunities(lhr) {
  const audits    = lhr.audits || {};
  const weights   = buildCategoryWeightMap(lhr);
  const strategies = /** @type {import('../ai/types.js').Strategy[]} */ ([]);

  const out = [];

  for (const [id, audit] of Object.entries(audits)) {
    if (audit.score === 1 || audit.score == null) continue; // pass / informational
    if (!weights[id]) continue;                             // not in any category

    out.push({
      id,
      title:       audit.title || id,
      description: audit.description || '',
      impactScore: 0, // will be set by scoring.js
      priority:    'medium', // placeholder, set by scoring.js
      displayValue:       audit.displayValue || '',
      affectedStrategies: strategies,
      // Keep lightweight numeric data for scoring
      _score:        audit.score,
      _numericValue: audit.numericValue,
      _weight:       weights[id],
      _hasItems:     Array.isArray(audit.details?.items) && audit.details.items.length > 0,
      _overallSavingsBytes: audit.details?.overallSavingsBytes ?? 0,
      _category:     getPrimaryCategory(id, lhr),
    });
  }

  return out;
}

/**
 * Build a map of auditId → combined weight across all categories.
 *
 * @param {Object} lhr
 * @returns {Record<string, number>}
 */
function buildCategoryWeightMap(lhr) {
  /** @type {Record<string, number>} */
  const weights = {};
  for (const cat of Object.values(lhr.categories || {})) {
    for (const ref of (cat.auditRefs || [])) {
      weights[ref.id] = (weights[ref.id] || 0) + (ref.weight || 1);
    }
  }
  return weights;
}

/**
 * @param {string} auditId
 * @param {Object} lhr
 * @returns {string}
 */
function getPrimaryCategory(auditId, lhr) {
  for (const [catId, cat] of Object.entries(lhr.categories || {})) {
    if (cat.auditRefs?.some(r => r.id === auditId)) return catId;
  }
  return 'other';
}

/**
 * Convert 0–1 float to 0–100 integer.
 *
 * @param {number | null | undefined} score
 * @returns {number}
 */
function toPercent(score) {
  if (score == null) return 0;
  return Math.round(score * 100);
}
