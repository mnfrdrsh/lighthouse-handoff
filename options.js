// options.js — Lighthouse Handoff Settings
// Updated for V2: AI engine settings section added.

import { loadAISettings, saveAISettings } from './src/ai/engine.js';

const elements = {
  apiKeyInput:        document.getElementById('api-key-input'),
  toggleVisibilityBtn: document.getElementById('toggle-visibility'),
  saveBtn:            document.getElementById('save-btn'),
  clearBtn:           document.getElementById('clear-btn'),
  status:             document.getElementById('save-status'),
  openConsoleBtn:     document.getElementById('open-console-btn'),

  // AI settings
  providerSelect:     document.getElementById('ai-provider-select'),
  outputModeSelect:   document.getElementById('output-mode-select'),
  saveAiBtn:          document.getElementById('save-ai-btn'),
  aiStatus:           document.getElementById('ai-status'),
  providerHint:       document.getElementById('provider-hint'),
};

let isKeyVisible = false;

/**
 * Initialize the options page
 */
async function init() {
  await loadSavedKey();
  await loadSavedAISettings();
  setupEventListeners();
}

// ---------------------------------------------------------------------------
// API Key
// ---------------------------------------------------------------------------

/**
 * Load previously saved API key (if any)
 */
async function loadSavedKey() {
  try {
    const result = await chrome.storage.sync.get(['psiApiKey']);
    if (result.psiApiKey) {
      elements.apiKeyInput.value = result.psiApiKey;
      showStatus('API key loaded from storage', 'success');
      setTimeout(() => clearStatus(), 1600);
    }
  } catch (error) {
    console.error({ error }, 'Failed to load API key');
    showStatus('Could not load saved key', 'error');
  }
}

/**
 * Save the API key to chrome.storage.sync
 */
async function saveKey() {
  const key = elements.apiKeyInput.value.trim();

  if (!key) {
    showStatus('Please enter an API key', 'error');
    return;
  }

  if (!key.startsWith('AIza')) {
    const proceed = confirm(
      'This key does not look like a standard Google API key (usually starts with AIza).\n\nSave anyway?'
    );
    if (!proceed) return;
  }

  try {
    await chrome.storage.sync.set({ psiApiKey: key });
    showStatus('API key saved successfully ✓ (side panel updates automatically)', 'success');
    setTimeout(() => { window.close(); }, 800);
  } catch (error) {
    console.error({ error }, 'Failed to save API key');
    showStatus('Failed to save key. Please try again.', 'error');
  }
}

/**
 * Clear the saved API key
 */
async function clearKey() {
  const confirmed = confirm('Remove the saved PageSpeed Insights API key?');
  if (!confirmed) return;

  try {
    await chrome.storage.sync.remove(['psiApiKey']);
    elements.apiKeyInput.value = '';
    showStatus('API key cleared', 'success');
  } catch (error) {
    console.error({ error }, 'Failed to clear API key');
    showStatus('Failed to clear key', 'error');
  }
}

/**
 * Toggle password visibility
 */
function toggleKeyVisibility() {
  isKeyVisible = !isKeyVisible;
  elements.apiKeyInput.type = isKeyVisible ? 'text' : 'password';
  elements.toggleVisibilityBtn.textContent = isKeyVisible ? 'Hide' : 'Show';
}

// ---------------------------------------------------------------------------
// AI Settings
// ---------------------------------------------------------------------------

const PROVIDER_HINTS = {
  mock:   'The Mock provider generates deterministic recommendations from your report data — no AI model or internet connection required.',
  ollama: 'Ollama runs models locally on your machine. Requires Ollama to be installed and running.',
  openai: 'OpenAI sends your normalized report data to the OpenAI API. Requires an OpenAI API key.',
  claude: 'Claude sends your normalized report data to the Anthropic API. Requires an Anthropic API key.',
  liquid: 'Liquid AI runs the LEAP SDK locally. Phase 2 implementation.',
  'liquid-local': 'Connects to a local Liquid LEAP companion app over http://localhost.',
};

async function loadSavedAISettings() {
  try {
    const settings = await loadAISettings();
    elements.providerSelect.value    = settings.provider;
    elements.outputModeSelect.value  = settings.outputMode;
    updateProviderHint(settings.provider);
  } catch (error) {
    console.error({ error }, 'Failed to load AI settings');
  }
}

async function saveAISettingsHandler() {
  const provider   = elements.providerSelect.value;
  const outputMode = elements.outputModeSelect.value;

  try {
    await saveAISettings({ provider, outputMode });
    showAiStatus('AI settings saved ✓', 'success');
    setTimeout(() => clearAiStatus(), 2000);
  } catch (error) {
    console.error({ error }, 'Failed to save AI settings');
    showAiStatus('Failed to save AI settings', 'error');
  }
}

function updateProviderHint(provider) {
  elements.providerHint.textContent = PROVIDER_HINTS[provider] ?? '';
  
  if (provider === 'liquid-local') {
    checkLiquidHealth();
  }
}

async function checkLiquidHealth() {
  showAiStatus('Checking Liquid companion status...', 'info');
  try {
    const { LiquidLocalProvider } = await import('./src/ai/providers/liquid-local.js');
    const p = new LiquidLocalProvider();
    const health = await p.checkHealth();
    if (health.ready) {
      showAiStatus(`Liquid Companion: Connected (Model: ${health.model})`, 'success');
    } else {
      showAiStatus('Liquid Companion: Not running', 'error');
    }
  } catch (err) {
    showAiStatus('Liquid Companion: Not running', 'error');
  }
}

function showAiStatus(message, type) {
  elements.aiStatus.textContent = message;
  elements.aiStatus.className = `status ${type}`;
}

function clearAiStatus() {
  elements.aiStatus.textContent = '';
  elements.aiStatus.className = 'status';
}

// ---------------------------------------------------------------------------
// Event listeners
// ---------------------------------------------------------------------------

function setupEventListeners() {
  elements.saveBtn.addEventListener('click', saveKey);
  elements.clearBtn.addEventListener('click', clearKey);
  elements.toggleVisibilityBtn.addEventListener('click', toggleKeyVisibility);
  elements.saveAiBtn.addEventListener('click', saveAISettingsHandler);

  elements.providerSelect.addEventListener('change', () => {
    updateProviderHint(elements.providerSelect.value);
    clearAiStatus();
  });

  elements.outputModeSelect.addEventListener('change', () => clearAiStatus());

  const openConsole = document.getElementById('open-console-btn');
  if (openConsole) {
    openConsole.addEventListener('click', () => {
      chrome.tabs.create({ url: 'https://console.cloud.google.com/apis/credentials' });
    });
  }

  elements.apiKeyInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') saveKey();
  });

  elements.apiKeyInput.addEventListener('input', () => {
    if (elements.status.textContent) clearStatus();
  });
}

// ---------------------------------------------------------------------------
// Status helpers
// ---------------------------------------------------------------------------

function showStatus(message, type) {
  elements.status.textContent = message;
  elements.status.className = `status ${type}`;
}

function clearStatus() {
  elements.status.textContent = '';
  elements.status.className = 'status';
}

init();
