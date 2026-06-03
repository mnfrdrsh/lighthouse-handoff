// src/ai/types.js
// Shared type definitions for the AI Engine layer.
// All types are expressed as JSDoc for IDE support in plain JS.

/**
 * @typedef {'mobile' | 'desktop'} Strategy
 */

/**
 * @typedef {'critical' | 'high' | 'medium' | 'low'} Priority
 */

/**
 * @typedef {'mock' | 'openai' | 'claude' | 'ollama' | 'liquid'} ProviderName
 */

/**
 * @typedef {'cursor' | 'claude-code' | 'github' | 'client'} OutputMode
 */

/**
 * @typedef {Object} Opportunity
 * @property {string} id
 * @property {string} title
 * @property {string} description
 * @property {number} impactScore
 * @property {Priority} priority
 * @property {string} [displayValue]
 * @property {string[]} [affectedStrategies]
 */

/**
 * @typedef {Object} LighthouseSummary
 * @property {string} url
 * @property {Strategy} strategy
 * @property {{ performance: number, accessibility: number, seo: number, bestPractices: number }} scores
 * @property {{ lcp: number, cls: number, inp: number, tbt: number }} metrics
 * @property {Opportunity[]} opportunities
 */

/**
 * @typedef {Object} RankedIssue
 * @property {Priority} priority
 * @property {string} id
 * @property {string} title
 * @property {number} impactScore
 * @property {string} [displayValue]
 * @property {string} [description]
 * @property {string[]} [affectedStrategies]
 */

/**
 * @typedef {Object} PriorityFix
 * @property {string} title
 * @property {string} reasoning
 * @property {string[]} instructions
 */

/**
 * @typedef {Object} AIAnalysis
 * @property {string} executiveSummary
 * @property {string[]} quickWins
 * @property {PriorityFix[]} priorityFixes
 * @property {string[]} acceptanceCriteria
 */

/**
 * @typedef {Object} AISettings
 * @property {ProviderName} provider
 * @property {OutputMode} outputMode
 */

export const DEFAULT_SETTINGS = /** @type {AISettings} */ ({
  provider: 'mock',
  outputMode: 'cursor',
});

export const PROVIDERS = /** @type {ProviderName[]} */ (['mock', 'openai', 'claude', 'ollama', 'liquid']);
export const OUTPUT_MODES = /** @type {OutputMode[]} */ (['cursor', 'claude-code', 'github', 'client']);
