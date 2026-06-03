// src/lighthouse/parser.js
// Thin facade that combines normalizer + scoring into a single call.
// This is the public API for the lighthouse layer.

import { normalizeResults, normalizeLighthouseResult } from './normalizer.js';
import { buildRankedIssueList } from './scoring.js';

export { normalizeLighthouseResult, normalizeResults };

/**
 * Full pipeline: normalize + rank for a set of PSI results.
 *
 * @param {Array<{strategy: import('../ai/types.js').Strategy, success: boolean, data: Object}>} results
 * @returns {{ summaries: import('../ai/types.js').LighthouseSummary[], rankedIssues: import('../ai/types.js').RankedIssue[] }}
 */
export function parseAndRank(results) {
  const summaries = normalizeResults(results);

  // Merge ranked issues across strategies, deduplicate by id, keep worst
  const issueMap = new Map();

  for (const summary of summaries) {
    for (const issue of buildRankedIssueList(summary)) {
      const existing = issueMap.get(issue.id);
      if (!existing || issue.impactScore > existing.impactScore) {
        issueMap.set(issue.id, {
          ...issue,
          affectedStrategies: existing
            ? [...new Set([...(existing.affectedStrategies ?? []), summary.strategy])]
            : [summary.strategy],
        });
      } else if (existing) {
        // Merge strategies without replacing impactScore
        existing.affectedStrategies = [
          ...new Set([...(existing.affectedStrategies ?? []), summary.strategy]),
        ];
      }
    }
  }

  const rankedIssues = Array.from(issueMap.values())
    .sort((a, b) => b.impactScore - a.impactScore);

  return { summaries, rankedIssues };
}
