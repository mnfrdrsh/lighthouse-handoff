import test from 'node:test';
import assert from 'node:assert/strict';
import { LiquidLocalProvider } from '../src/ai/providers/liquid-local.js';
import { createProvider } from '../src/ai/engine.js';

test('LiquidLocalProvider', async (t) => {
  const mockSummary = {
    url: 'https://example.com',
    strategy: 'mobile',
    scores: { performance: 50, accessibility: 90, seo: 90, bestPractices: 90 },
    metrics: { lcp: 5000, cls: 0.1, inp: 200, tbt: 300 },
    opportunities: []
  };

  const validResponse = {
    executiveSummary: 'Test summary',
    quickWins: ['Win 1'],
    priorityFixes: [],
    acceptanceCriteria: ['Crit 1']
  };

  await t.test('sends correct payload and handles success', async () => {
    let capturedBody;
    global.fetch = async (url, options) => {
      capturedBody = JSON.parse(options.body);
      return {
        ok: true,
        json: async () => validResponse
      };
    };

    const provider = new LiquidLocalProvider();
    const result = await provider.analyze(mockSummary);

    assert.equal(capturedBody.outputMode, 'cursor');
    assert.equal(capturedBody.schemaVersion, '1.0');
    assert.equal(capturedBody.report.url, 'https://example.com');
    assert.deepEqual(result, validResponse);
  });

  await t.test('handles connection failure', async () => {
    global.fetch = async () => {
      throw new Error('fetch failed');
    };

    const provider = new LiquidLocalProvider();
    await assert.rejects(
      provider.analyze(mockSummary),
      /Liquid Local Companion is not running/
    );
  });

  await t.test('handles timeout', async () => {
    global.fetch = async (url, options) => {
      const err = new Error('AbortError');
      err.name = 'AbortError';
      throw err;
    };

    const provider = new LiquidLocalProvider();
    await assert.rejects(
      provider.analyze(mockSummary),
      /Liquid Local Companion request timed out/
    );
  });

  await t.test('rejects malformed response', async () => {
    global.fetch = async () => ({
      ok: true,
      json: async () => ({ executiveSummary: 'missing arrays' })
    });

    const provider = new LiquidLocalProvider();
    await assert.rejects(
      provider.analyze(mockSummary),
      /Missing quickWins array/
    );
  });

  await t.test('engine can select liquid-local', () => {
    const provider = createProvider('liquid-local');
    assert.ok(provider instanceof LiquidLocalProvider);
  });
});
