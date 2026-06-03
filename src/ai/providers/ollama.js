// src/ai/providers/ollama.ts stub
// src/ai/providers/ollama.js
// Ollama Local Provider stub.
// Implement this file when Ollama support is needed.
// The interface contract is defined in engine.js (AIProvider).

/** @typedef {import('../types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../types.js').AIAnalysis} AIAnalysis */

export class OllamaProvider {
  /**
   * @param {{ model?: string, baseUrl?: string }} [options]
   */
  constructor(options = {}) {
    this.model   = options.model   ?? 'llama3';
    this.baseUrl = options.baseUrl ?? 'http://localhost:11434';
  }

  get name() { return 'ollama'; }

  /**
   * @param {LighthouseSummary} _summary
   * @returns {Promise<AIAnalysis>}
   */
  async analyze(_summary) {
    throw new Error('OllamaProvider is not yet implemented. Set provider to "mock" in AI Settings.');
  }
}
