// src/ai/engine.js
// AI Engine — the single entry point for AI analysis.
// Resolves the configured provider and delegates analysis.

import { MockProvider }   from './providers/mock.js';
import { OllamaProvider } from './providers/ollama.js';
import { OpenAIProvider } from './providers/openai.js';
import { DEFAULT_SETTINGS } from './types.js';

/** @typedef {import('./types.js').AIProvider} AIProvider */
/** @typedef {import('./types.js').LighthouseSummary} LighthouseSummary */
/** @typedef {import('./types.js').AIAnalysis} AIAnalysis */
/** @typedef {import('./types.js').AISettings} AISettings */
/** @typedef {import('./types.js').ProviderName} ProviderName */

const AI_SETTINGS_KEY = 'aiSettings';

// ---------------------------------------------------------------------------
// Provider factory
// ---------------------------------------------------------------------------

/**
 * Instantiate the correct provider for a given name.
 *
 * @param {ProviderName} name
 * @param {Record<string, unknown>} [providerOptions]
 * @returns {AIProvider}
 */
export function createProvider(name, providerOptions = {}) {
  switch (name) {
    case 'mock':   return new MockProvider();
    case 'ollama': return new OllamaProvider(providerOptions);
    case 'openai': return new OpenAIProvider(providerOptions);
    case 'claude':
    case 'liquid':
      // Stubs — Phase 2 implementations go here
      throw new Error(`Provider "${name}" is not yet implemented.`);
    default:
      throw new Error(`Unknown AI provider: "${name}".`);
  }
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

/**
 * Run AI analysis on a LighthouseSummary using the currently configured provider.
 *
 * @param {LighthouseSummary} summary
 * @param {AISettings} [settingsOverride] - Override stored settings (useful in tests)
 * @returns {Promise<AIAnalysis>}
 */
export async function runAnalysis(summary, settingsOverride) {
  const settings = settingsOverride ?? await loadAISettings();
  const provider = createProvider(settings.provider);
  return provider.analyze(summary);
}

// ---------------------------------------------------------------------------
// Settings persistence (chrome.storage.local)
// ---------------------------------------------------------------------------

/**
 * Load AI settings from storage, falling back to defaults.
 *
 * @returns {Promise<AISettings>}
 */
export async function loadAISettings() {
  try {
    const result = await chrome.storage.local.get(AI_SETTINGS_KEY);
    return { ...DEFAULT_SETTINGS, ...(result[AI_SETTINGS_KEY] ?? {}) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

/**
 * Persist AI settings to storage.
 *
 * @param {Partial<AISettings>} patch
 * @returns {Promise<AISettings>}
 */
export async function saveAISettings(patch) {
  const current = await loadAISettings();
  const updated = { ...current, ...patch };
  await chrome.storage.local.set({ [AI_SETTINGS_KEY]: updated });
  return updated;
}
