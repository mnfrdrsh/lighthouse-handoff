// src/ai/providers/liquid-local.js
// Liquid Local Companion Provider (Spike)
// Sends normalized Lighthouse data to a local companion app running Liquid LEAP.

/** @typedef {import('../types.js').AIProvider} AIProvider */
/** @typedef {import('../types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('../types.js').AIAnalysis} AIAnalysis */
/** @typedef {import('../types.js').AISettings} AISettings */

export class LiquidLocalProvider {
  /**
   * @param {Record<string, unknown>} options
   */
  constructor(options = {}) {
    this.endpoint = options.endpoint || 'http://localhost:31313/analyze';
    this.healthEndpoint = options.healthEndpoint || 'http://localhost:31313/health';
    this.timeoutMs = 25000; // 25 seconds fast-fail
    this.model = options.model || '';
  }

  /**
   * Analyzes the summary using the local Liquid companion.
   *
   * @param {LighthouseSummary} summary
   * @returns {Promise<AIAnalysis>}
   */
  async analyze(summary) {
    const { rankIssues } = await import('../../lighthouse/scoring.js');
    // We duplicate the rank logic here or expect the caller to pass it.
    // The prompt requires we accept ranked issues. The engine currently just passes summary.
    // But engine.js signature is `analyze(summary)`. We can derive rankedIssues here.
    const rankedIssues = rankIssues(summary);

    const payload = {
      report: summary,
      rankedIssues,
      outputMode: 'cursor', // Default, options can inject actual
      schemaVersion: '1.0',
      modelId: this.model
    };

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);

    let response;
    try {
      response = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
    } catch (err) {
      if (err.name === 'AbortError') {
        throw new Error('Liquid Local Companion request timed out after 25s.');
      }
      throw new Error('Liquid Local Companion is not running. Start the companion app or switch provider back to Mock.');
    } finally {
      clearTimeout(timeout);
    }

    if (!response.ok) {
      throw new Error(`Liquid Local Companion returned ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();
    this.validateResponse(data);
    return data;
  }

  /**
   * Checks the health of the companion app.
   *
   * @returns {Promise<{status: string, provider: string, model: string, ready: boolean}>}
   */
  async checkHealth() {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2000);

    try {
      const response = await fetch(this.healthEndpoint, { signal: controller.signal });
      if (!response.ok) throw new Error('Not OK');
      const data = await response.json();
      return data;
    } catch (err) {
      return { status: 'error', provider: 'liquid', model: 'unknown', ready: false };
    } finally {
      clearTimeout(timeout);
    }
  }

  /**
   * Validates that the returned JSON matches the AIAnalysis shape.
   *
   * @param {any} data
   */
  validateResponse(data) {
    if (!data) throw new Error('Received empty response from Liquid companion.');
    if (typeof data.executiveSummary !== 'string') throw new Error('Missing executiveSummary in response.');
    if (!Array.isArray(data.quickWins)) throw new Error('Missing quickWins array in response.');
    if (!Array.isArray(data.priorityFixes)) throw new Error('Missing priorityFixes array in response.');
    if (!Array.isArray(data.acceptanceCriteria)) throw new Error('Missing acceptanceCriteria array in response.');
  }
}
