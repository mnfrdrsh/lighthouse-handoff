// options.js — Lighthouse Handoff Settings

const elements = {
  apiKeyInput: document.getElementById('api-key-input'),
  toggleVisibilityBtn: document.getElementById('toggle-visibility'),
  saveBtn: document.getElementById('save-btn'),
  clearBtn: document.getElementById('clear-btn'),
  status: document.getElementById('save-status'),
  openConsoleBtn: document.getElementById('open-console-btn'),
};

let isKeyVisible = false;

/**
 * Initialize the options page
 */
async function init() {
  await loadSavedKey();
  setupEventListeners();
}

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
    console.error('Failed to load API key:', error);
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
    
    // Close the options page after a short delay so the user sees the confirmation
    setTimeout(() => {
      window.close();
    }, 800);
  } catch (error) {
    console.error('Failed to save key:', error);
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
    console.error('Failed to clear key:', error);
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

/**
 * Set up all event listeners
 */
function setupEventListeners() {
  elements.saveBtn.addEventListener('click', saveKey);
  elements.clearBtn.addEventListener('click', clearKey);
  elements.toggleVisibilityBtn.addEventListener('click', toggleKeyVisibility);

  // Quick link to create the API key
  const openConsole = document.getElementById('open-console-btn');
  if (openConsole) {
    openConsole.addEventListener('click', () => {
      chrome.tabs.create({ url: 'https://console.cloud.google.com/apis/credentials' });
    });
  }

  // Allow pressing Enter in the input to save
  elements.apiKeyInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      saveKey();
    }
  });

  // Clear status when user starts typing again
  elements.apiKeyInput.addEventListener('input', () => {
    if (elements.status.textContent) {
      clearStatus();
    }
  });
}

/**
 * Show status message
 */
function showStatus(message, type) {
  elements.status.textContent = message;
  elements.status.className = `status ${type}`;
}

/**
 * Clear status message
 */
function clearStatus() {
  elements.status.textContent = '';
  elements.status.className = 'status';
}

// Boot the options page
// Script is included at the end of the body, so DOM is ready. Call init directly.
init();
