// sidepanel.js — Lighthouse Handoff Side Panel (Phase 4+)

import { generateReport } from './report-builder.js';

const elements = {
  currentUrl: document.getElementById('current-url'),
  copyUrlBtn: document.getElementById('copy-url-btn'),
  manualUrl: document.getElementById('manual-url'),
  strategyMobile: document.getElementById('strategy-mobile'),
  strategyDesktop: document.getElementById('strategy-desktop'),
  catPerformance: document.getElementById('cat-performance'),
  catAccessibility: document.getElementById('cat-accessibility'),
  catBestPractices: document.getElementById('cat-best-practices'),
  catSeo: document.getElementById('cat-seo'),
  apiKeyStatus: document.getElementById('api-key-status'),
  generateBtn: document.getElementById('generate-btn'),
  openOptionsBtn: document.getElementById('open-options-btn'),
  statusMessage: document.getElementById('status-message'),
  statusArea: document.getElementById('status-area'),
  loadingIndicator: document.getElementById('loading-indicator'),
  loadingText: document.getElementById('loading-text'),
  resultsPreview: document.getElementById('results-preview'),
  downloadBtn: document.getElementById('download-btn'),
  refreshUrlBtn: document.getElementById('refresh-url-btn'),

  // History elements
  viewHistoryBtn: document.getElementById('view-history-btn'),
  historyView: document.getElementById('history-view'),
  backToGenerateBtn: document.getElementById('back-to-generate-btn'),
  historyList: document.getElementById('history-list'),
  historyDetail: document.getElementById('history-detail'),
  closeDetailBtn: document.getElementById('close-detail-btn'),
  historyDetailUrl: document.getElementById('history-detail-url'),
  historyDetailDate: document.getElementById('history-detail-date'),
  historyDetailContent: document.getElementById('history-detail-content'),
  copyHistoryBtn: document.getElementById('copy-history-btn'),
  downloadHistoryBtn: document.getElementById('download-history-btn'),
  deleteHistoryBtn: document.getElementById('delete-history-btn'),
};

let currentTabUrl = '';
let lastMarkdown = null;
let lastUrl = '';

let currentHistoryReport = null; // For viewing in detail

/**
 * Initialize the side panel
 */
async function init() {
  await loadCurrentTabUrl();
  await updateApiKeyStatus();
  setupEventListeners();
  updateGenerateButtonState();

  // Listen for tab changes so the URL updates when user switches tabs.
  // Use the specific tab from the event when possible for reliability in side panels.
  chrome.tabs.onActivated.addListener((activeInfo) => {
    chrome.tabs.get(activeInfo.tabId).then(loadCurrentTabFromTab).catch(() => loadCurrentTabUrl());
  });
  chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.status === 'complete' && tab) {
      loadCurrentTabFromTab(tab);
    } else if (changeInfo.status === 'complete') {
      loadCurrentTabUrl();
    }
  });

  // Listen for storage changes so API key status updates live if user adds key in Options while side panel is open
  chrome.storage.onChanged.addListener((changes, namespace) => {
    if (namespace === 'sync' && changes.psiApiKey) {
      updateApiKeyStatus();
    }
  });

  // Extra robustness: re-detect URL if the browser window focus changes (e.g. switching windows)
  chrome.windows.onFocusChanged.addListener((windowId) => {
    if (windowId !== chrome.windows.WINDOW_ID_NONE) {
      loadCurrentTabUrl();
    }
  });
}

/**
 * Load the URL from the active tab using query (fallback)
 */
async function loadCurrentTabUrl() {
  try {
    // Use lastFocusedWindow for better reliability in side panels
    const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    if (tab) {
      loadCurrentTabFromTab(tab);
    }
  } catch (error) {
    console.error('Error querying current tab:', error);
    if (elements.currentUrl) {
      elements.currentUrl.textContent = 'Error detecting URL';
      elements.currentUrl.style.color = '#dc2626';
    }
    updateGenerateButtonState();
  }
}

/**
 * Set the current tab URL from a tab object (preferred when we have the tab from event)
 */
function loadCurrentTabFromTab(tab) {
  if (!tab || !tab.url) {
    if (elements.currentUrl) {
      elements.currentUrl.textContent = 'Could not detect URL';
      elements.currentUrl.style.color = '#94a3b8';
    }
    updateGenerateButtonState();
    return;
  }

  if (tab.url.startsWith('http://') || tab.url.startsWith('https://')) {
    currentTabUrl = tab.url;
    if (elements.currentUrl) {
      elements.currentUrl.textContent = tab.url;
      elements.currentUrl.style.color = '#0f172a';
    }
  } else {
    if (elements.currentUrl) {
      elements.currentUrl.textContent = 'Not a web page (chrome://, etc.)';
      elements.currentUrl.style.color = '#94a3b8';
    }
  }
  updateGenerateButtonState();
}

/**
 * Update the API key status indicator
 */
async function updateApiKeyStatus() {
  if (!elements.apiKeyStatus) return;
  try {
    const result = await chrome.storage.sync.get(['psiApiKey']);
    const hasKey = result.psiApiKey && result.psiApiKey.length > 10;

    if (hasKey) {
      elements.apiKeyStatus.textContent = 'Configured ✓';
      elements.apiKeyStatus.className = 'status-value ready';
    } else {
      elements.apiKeyStatus.textContent = 'Not set — click Manage to add your key';
      elements.apiKeyStatus.className = 'status-value missing';
    }
  } catch (error) {
    console.error('Error checking API key:', error);
    elements.apiKeyStatus.textContent = 'Error';
    elements.apiKeyStatus.className = 'status-value missing';
  }
}

/**
 * Set up all event listeners
 */
function setupEventListeners() {
  elements.copyUrlBtn.addEventListener('click', handleCopyUrl);
  elements.manualUrl.addEventListener('input', updateGenerateButtonState);

  // Strategy & Category checkboxes
  elements.strategyMobile.addEventListener('change', updateGenerateButtonState);
  elements.strategyDesktop.addEventListener('change', updateGenerateButtonState);
  elements.catPerformance.addEventListener('change', updateGenerateButtonState);
  elements.catAccessibility.addEventListener('change', updateGenerateButtonState);
  elements.catBestPractices.addEventListener('change', updateGenerateButtonState);
  elements.catSeo.addEventListener('change', updateGenerateButtonState);

  elements.generateBtn.addEventListener('click', handleGenerateClick);
  elements.openOptionsBtn.addEventListener('click', () => {
    chrome.runtime.openOptionsPage();
  });

  if (elements.downloadBtn) {
    elements.downloadBtn.addEventListener('click', handleDownloadReport);
  }

  if (elements.refreshUrlBtn) {
    elements.refreshUrlBtn.addEventListener('click', () => {
      loadCurrentTabUrl();
    });
  }

  // History
  if (elements.viewHistoryBtn) {
    elements.viewHistoryBtn.addEventListener('click', showHistoryView);
  }
  if (elements.backToGenerateBtn) {
    elements.backToGenerateBtn.addEventListener('click', showGenerateView);
  }
  if (elements.closeDetailBtn) {
    elements.closeDetailBtn.addEventListener('click', () => {
      elements.historyDetail.hidden = true;
      elements.historyList.hidden = false;
    });
  }
  if (elements.copyHistoryBtn) {
    elements.copyHistoryBtn.addEventListener('click', handleCopyHistoryReport);
  }
  if (elements.downloadHistoryBtn) {
    elements.downloadHistoryBtn.addEventListener('click', handleDownloadHistoryReport);
  }
  if (elements.deleteHistoryBtn) {
    elements.deleteHistoryBtn.addEventListener('click', handleDeleteHistoryReport);
  }
}

function handleCopyUrl() {
  const urlToCopy = elements.manualUrl.value.trim() || currentTabUrl;
  if (!urlToCopy) return;

  navigator.clipboard.writeText(urlToCopy)
    .then(() => {
      showStatus('URL copied to clipboard', 'success');
      setTimeout(clearStatus, 1400);
    })
    .catch(() => {
      const ta = document.createElement('textarea');
      ta.value = urlToCopy;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      showStatus('URL copied', 'success');
      setTimeout(clearStatus, 1400);
    });
}

/**
 * Enable/disable generate button based on form state
 */
function updateGenerateButtonState() {
  const hasStrategy = elements.strategyMobile.checked || elements.strategyDesktop.checked;
  const hasCategory =
    elements.catPerformance.checked ||
    elements.catAccessibility.checked ||
    elements.catBestPractices.checked ||
    elements.catSeo.checked;

  const hasUrl = currentTabUrl || elements.manualUrl.value.trim().length > 0;

  elements.generateBtn.disabled = !(hasStrategy && hasCategory && hasUrl);
}

/**
 * Main generate handler
 */
async function handleGenerateClick() {
  const url = elements.manualUrl.value.trim() || currentTabUrl;

  const strategies = [];
  if (elements.strategyMobile.checked) strategies.push('mobile');
  if (elements.strategyDesktop.checked) strategies.push('desktop');

  const categories = [];
  if (elements.catPerformance.checked) categories.push('performance');
  if (elements.catAccessibility.checked) categories.push('accessibility');
  if (elements.catBestPractices.checked) categories.push('best-practices');
  if (elements.catSeo.checked) categories.push('seo');

  if (!strategies.length || !categories.length) {
    showStatus('Please select at least one strategy and one category.', 'error');
    return;
  }

  setLoadingState(true, `Running PageSpeed Insights (${strategies.join(' + ')})...`);
  hideResultsPreview();
  clearStatus();

  lastMarkdown = null;

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'RUN_PSI_AUDIT',
      payload: { url, strategies, categories },
    });

    if (!response) {
      throw new Error('No response from background script.');
    }

    if (response.success) {
      lastUrl = response.url;

      try {
        const markdown = generateReport(response.results, response.url);
        lastMarkdown = markdown;

        showStatus(`Report generated for ${response.results.length} strategy(ies).`, 'success');
        showResultsPreview(markdown);

        // Auto-save report locally
        await saveReportToHistory({
          url: response.url,
          markdown,
          strategies: response.results.map(r => r.strategy),
        });

      } catch (genError) {
        console.error('Report generation failed:', genError);
        showStatus('Report generation encountered an issue. Please try again or check the URL.', 'error');
        hideResultsPreview();
      }
    } else {
      handlePSIError(response);
    }
  } catch (error) {
    console.error('Error calling background:', error);
    showStatus(`Error: ${error.message}`, 'error');
  } finally {
    setLoadingState(false);
  }
}

function handlePSIError(response) {
  let message = response.message || 'Unknown error';

  if (response.error === 'NO_API_KEY') {
    message = 'No API key set. Click "Manage" to add your PageSpeed Insights key.';
    setTimeout(() => {
      if (confirm('Open Options page to add your API key now?')) {
        chrome.runtime.openOptionsPage();
      }
    }, 600);
  }

  showStatus(message, 'error');
  console.warn('[Lighthouse Handoff] PSI Error:', response);
}

function showResultsPreview(markdown) {
  elements.resultsPreview.hidden = false;

  const previewNote = elements.resultsPreview.querySelector('.preview-note');
  const header = elements.resultsPreview.querySelector('.preview-header span');

  if (header) header.textContent = 'Report ready';

  if (previewNote) {
    const teaser = markdown.slice(0, 260).replace(/\n/g, ' ').trim() + '...';
    previewNote.innerHTML = `<strong>Agent-ready Markdown generated.</strong> ${teaser}`;
  }
  elements.downloadBtn.textContent = 'Download .md';
  elements.downloadBtn.disabled = false;
}

function hideResultsPreview() {
  if (elements.resultsPreview) {
    elements.resultsPreview.hidden = true;
  }
}

function handleDownloadReport() {
  if (!lastMarkdown) return;

  const hostname = new URL(lastUrl || 'site').hostname.replace(/\./g, '-');
  const filename = `pagespeed-report-${hostname}.md`;
  const blob = new Blob([lastMarkdown], { type: 'text/markdown' });

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);

  showStatus('Markdown report downloaded', 'success');
  setTimeout(clearStatus, 1600);
}

function setLoadingState(isLoading, message = '') {
  elements.generateBtn.disabled = isLoading;

  if (isLoading) {
    elements.loadingIndicator.hidden = false;
    if (message) elements.loadingText.textContent = message;
    elements.generateBtn.textContent = 'Running...';
  } else {
    elements.loadingIndicator.hidden = true;
    elements.generateBtn.textContent = 'Generate Agent Report';
    updateGenerateButtonState();
  }
}

function showStatus(message, type = '') {
  elements.statusMessage.textContent = message;
  elements.statusMessage.className = `status-message ${type}`;
}

function clearStatus() {
  elements.statusMessage.textContent = '';
  elements.statusMessage.className = 'status-message';
}

/* ========================================================================== */
/*                        LOCAL REPORT HISTORY                                */
/* ========================================================================== */

const MAX_SAVED_REPORTS = 15;
const STORAGE_KEY = 'savedReports';

async function saveReportToHistory({ url, markdown, strategies }) {
  try {
    const { [STORAGE_KEY]: existing = [] } = await chrome.storage.local.get(STORAGE_KEY);

    const newReport = {
      id: Date.now().toString(36) + Math.random().toString(36).slice(2),
      url,
      timestamp: Date.now(),
      markdown,
      strategies: strategies || [],
    };

    const updated = [newReport, ...existing].slice(0, MAX_SAVED_REPORTS);

    await chrome.storage.local.set({ [STORAGE_KEY]: updated });
    console.log('[Lighthouse Handoff] Report saved to local history');
  } catch (err) {
    console.error('Failed to save report to history:', err);
  }
}

async function loadSavedReports() {
  const { [STORAGE_KEY]: reports = [] } = await chrome.storage.local.get(STORAGE_KEY);
  return reports;
}

async function showHistoryView() {
  const generateView = document.getElementById('generate-view');
  if (generateView) generateView.style.display = 'none';

  elements.historyView.hidden = false;
  elements.historyDetail.hidden = true;
  elements.historyList.hidden = false;

  const reports = await loadSavedReports();
  renderHistoryList(reports);
}

function showGenerateView() {
  elements.historyView.hidden = true;

  const generateView = document.getElementById('generate-view');
  if (generateView) generateView.style.display = '';

  // Force a fresh URL detection when returning from history view
  loadCurrentTabUrl();
  // Also refresh API key status in case it was added in options
  updateApiKeyStatus();
}

function renderHistoryList(reports) {
  elements.historyList.innerHTML = '';

  if (reports.length === 0) {
    elements.historyList.innerHTML = '<p style="color:#64748b; padding:12px;">No saved reports yet.</p>';
    return;
  }

  reports.forEach(report => {
    const item = document.createElement('div');
    item.className = 'history-item';

    const date = new Date(report.timestamp).toLocaleString();

    item.innerHTML = `
      <div class="history-item-info">
        <div class="history-item-url">${report.url}</div>
        <div class="history-item-date">${date}</div>
      </div>
      <div class="history-item-actions">
        <button class="btn btn-small view-btn">View</button>
      </div>
    `;

    item.querySelector('.view-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      showHistoryDetail(report);
    });

    elements.historyList.appendChild(item);
  });
}

function showHistoryDetail(report) {
  currentHistoryReport = report;

  elements.historyList.hidden = true;
  elements.historyDetail.hidden = false;

  elements.historyDetailUrl.textContent = report.url;
  elements.historyDetailDate.textContent = new Date(report.timestamp).toLocaleString();
  elements.historyDetailContent.textContent = report.markdown;
}

async function handleCopyHistoryReport() {
  if (!currentHistoryReport) return;
  await navigator.clipboard.writeText(currentHistoryReport.markdown);
  // Simple feedback
  const originalText = elements.copyHistoryBtn.textContent;
  elements.copyHistoryBtn.textContent = 'Copied!';
  setTimeout(() => {
    elements.copyHistoryBtn.textContent = originalText;
  }, 1200);
}

function handleDownloadHistoryReport() {
  if (!currentHistoryReport) return;

  const hostname = new URL(currentHistoryReport.url).hostname.replace(/\./g, '-');
  const filename = `pagespeed-report-${hostname}.md`;

  const blob = new Blob([currentHistoryReport.markdown], { type: 'text/markdown' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

async function handleDeleteHistoryReport() {
  if (!currentHistoryReport) return;

  const confirmed = confirm('Delete this saved report?');
  if (!confirmed) return;

  const { [STORAGE_KEY]: reports = [] } = await chrome.storage.local.get(STORAGE_KEY);
  const filtered = reports.filter(r => r.id !== currentHistoryReport.id);

  await chrome.storage.local.set({ [STORAGE_KEY]: filtered });

  // Go back to list
  elements.historyDetail.hidden = true;
  elements.historyList.hidden = false;

  const updatedReports = await loadSavedReports();
  renderHistoryList(updatedReports);
}

// Boot the side panel
document.addEventListener('DOMContentLoaded', init);
