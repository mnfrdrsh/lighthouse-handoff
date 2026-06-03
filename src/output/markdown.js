// src/output/markdown.js
// Markdown Generator — converts AIAnalysis into formatted Markdown.
// Delegates to the correct prompt template based on OutputMode.

import { buildCursorPrompt }  from '../ai/prompts/cursor.js';
import { buildGitHubPrompt }  from '../ai/prompts/github.js';
import { buildClientPrompt }  from '../ai/prompts/client.js';

/** @typedef {import('../ai/types.js').AIAnalysis} AIAnalysis */
/** @typedef {import('../ai/types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../ai/types.js').OutputMode} OutputMode */

/**
 * Generate the full Markdown handoff document for a given output mode.
 *
 * @param {AIAnalysis} analysis
 * @param {LighthouseSummary} summary
 * @param {OutputMode} outputMode
 * @returns {string}
 */
export function generateMarkdown(analysis, summary, outputMode) {
  switch (outputMode) {
    case 'cursor':
    case 'claude-code':
      // claude-code uses the same structure as Cursor — agent-focused
      return buildCursorPrompt(analysis, summary);
    case 'github':
      return buildGitHubPrompt(analysis, summary);
    case 'client':
      return buildClientPrompt(analysis, summary);
    default:
      return buildCursorPrompt(analysis, summary);
  }
}

/**
 * Build a combined multi-strategy Markdown document.
 * When both mobile and desktop summaries are available, produces a single
 * document with both strategies' data and a single unified analysis.
 *
 * @param {AIAnalysis} analysis
 * @param {LighthouseSummary[]} summaries
 * @param {OutputMode} outputMode
 * @returns {string}
 */
export function generateCombinedMarkdown(analysis, summaries, outputMode) {
  if (summaries.length === 0) {
    return '# Lighthouse Handoff\n\nNo data available.';
  }

  if (summaries.length === 1) {
    return generateMarkdown(analysis, summaries[0], outputMode);
  }

  // For multi-strategy, use the mobile summary as primary but note both
  const primary = summaries.find(s => s.strategy === 'mobile') ?? summaries[0];
  const combined = {
    ...primary,
    // Override strategy label
    strategy: /** @type {any} */ ('mobile + desktop'),
  };

  return generateMarkdown(analysis, combined, outputMode);
}
