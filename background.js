// background.js — Lighthouse Handoff Service Worker (Manifest V3)

import { runPSIForStrategies } from './api/psi-client.js';

// ============================================
// Side Panel Setup
// ============================================

// Make clicking the toolbar icon open the side panel
chrome.sidePanel
  .setPanelBehavior({ openPanelOnActionClick: true })
  .catch((error) => console.error('Failed to set side panel behavior:', error));

// ============================================
// Message Handling (from sidepanel / popup)
// ============================================

/**
 * Handle messages from the side panel (or popup)
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'RUN_PSI_AUDIT') {
    handlePSIAudit(message.payload, sendResponse);
    return true; // Keep the message channel open for async response
  }
});

/**
 * Main handler for running PSI audits
 */
async function handlePSIAudit(payload, sendResponse) {
  const { url, strategies, categories } = payload;

  try {
    // 1. Get API key from storage
    const storage = await chrome.storage.sync.get(['psiApiKey']);
    const apiKey = storage.psiApiKey;

    if (!apiKey) {
      sendResponse({
        success: false,
        error: 'NO_API_KEY',
        message: 'No PageSpeed Insights API key found. Please set one in Options.',
      });
      return;
    }

    // 2. Validate basic inputs
    if (!url || !strategies?.length || !categories?.length) {
      sendResponse({
        success: false,
        error: 'INVALID_INPUT',
        message: 'Missing URL, strategies, or categories.',
      });
      return;
    }

    // 3. Call the PSI API (supports multiple strategies in parallel)
    const results = await runPSIForStrategies({
      url,
      strategies,
      categories,
      apiKey,
    });

    // 4. Check if all calls failed
    const allFailed = results.every((r) => !r.success);
    if (allFailed) {
      const firstError = results[0]?.error || 'Unknown error';
      sendResponse({
        success: false,
        error: 'PSI_FAILED',
        message: firstError,
        partialResults: results,
      });
      return;
    }

    // 5. Success - return the raw results for now
    // (Phase 3 will transform these into beautiful Markdown)
    sendResponse({
      success: true,
      results,           // Array of { strategy, success, data }
      url,
      timestamp: Date.now(),
    });

  } catch (error) {
    console.error('[Lighthouse Handoff] Unexpected error in background:', error);
    sendResponse({
      success: false,
      error: 'UNEXPECTED_ERROR',
      message: error.message || 'An unexpected error occurred.',
    });
  }
}

// Optional: Log when the service worker starts (useful during development)
console.log('[Lighthouse Handoff] Service worker loaded');
