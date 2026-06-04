// src/ai/prompts/cursor.js
// Prompt template for Cursor output mode.
// Generates implementation-focused instructions optimised for use inside Cursor AI.

/** @typedef {import('../types.js').AIAnalysis} AIAnalysis */
/** @typedef {import('../types.js').LighthouseSummary} LighthouseSummary */

/**
 * Build a Cursor-optimised prompt from an AIAnalysis.
 * Assumes the user is working inside the Cursor editor.
 *
 * @param {AIAnalysis} analysis
 * @param {LighthouseSummary} summary
 * @returns {string}
 */
export function buildCursorPrompt(analysis, summary) {
  return `You are a performance engineering expert working inside Cursor.
Your task: implement the highest-priority performance fixes for ${summary.url}.
Strategy analysed: ${summary.strategy.toUpperCase()}.

## Context

${analysis.executiveSummary}

## Approach

Work through the priority fixes below in order.
For each fix:
1. Identify the relevant files by searching the codebase (use Cursor's @codebase or grep)
2. Determine the probable root cause from the reasoning provided
3. Implement the smallest targeted change that addresses the issue
4. Do NOT change unrelated code, visual design, or third-party integrations

## Priority Fixes

${analysis.priorityFixes.map((fix, i) => formatCursorFix(fix, i + 1)).join('\n\n')}

## Quick Wins (after priority fixes)

${analysis.quickWins.map(w => `- ${w}`).join('\n')}

## Acceptance Criteria

${analysis.acceptanceCriteria.map(c => `- [ ] ${c}`).join('\n')}

## Guardrails

- Do NOT redesign layouts, spacing, or visual hierarchy
- Do NOT change brand colors, typography, or existing UI components
- Do NOT remove or disable analytics or tracking scripts without explicit approval
- Do NOT introduce new third-party dependencies
- Preserve all existing functionality and interactive behaviour
- When in doubt, make the smallest possible change
`;
}

/**
 * @param {import('../types.js').PriorityFix} fix
 * @param {number} rank
 * @returns {string}
 */
function formatCursorFix(fix, rank) {
  const steps = fix.instructions.map((step, i) => `   ${i + 1}. ${step}`).join('\n');
  return `### Fix ${rank}: ${fix.title} (\`${fix.id}\`)

**Reasoning**: ${fix.reasoning}

**Implementation steps**:
${steps}`;
}
