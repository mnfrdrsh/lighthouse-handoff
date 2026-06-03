// src/ai/providers/openai.js
// OpenAI Provider stub.
// Implement this file when OpenAI support is needed.

/** @typedef {import('../types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../types.js').AIAnalysis} AIAnalysis */

export class OpenAIProvider {
  /**
   * @param {{ apiKey?: string, model?: string }} [options]
   */
  constructor(options = {}) {
    this.apiKey = options.apiKey ?? '';
    this.model  = options.model  ?? 'gpt-4o-mini';
  }

  get name() { return 'openai'; }

  /**
   * @param {LighthouseSummary} _summary
   * @returns {Promise<AIAnalysis>}
   */
  async analyze(_summary) {
    throw new Error('OpenAIProvider is not yet implemented. Set provider to "mock" in AI Settings.');
  }
}
