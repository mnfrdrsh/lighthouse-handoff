// sidepanel.js — Lighthouse Handoff
'use strict';

import { generateReport }              from './report-builder.js';
import { parseAndRank }               from './src/lighthouse/parser.js';
import { runAnalysis, loadAISettings } from './src/ai/engine.js';
import { generateCombinedMarkdown }   from './src/output/markdown.js';


const $ = id => document.getElementById(id);

const ui = {
  viewMain:    $('view-main'),
  viewHistory: $('view-history'),

  urlInput:        $('url-input'),
  strategyMobile:  $('strategy-mobile'),
  strategyDesktop: $('strategy-desktop'),
  btnGenerate:     $('btn-generate'),
  statusLine:      $('status-line'),
  recentList:      $('recent-history-list'),
  btnViewAll:      $('view-all-history-link'),
  btnHistory:      $('btn-history'),
  apiKeyStatus:    $('api-key-status'),
  openOptions:     $('open-options-btn'),
  btnBackMain:     $('btn-back-main'),
  historyList:     $('history-list'),
};

let currentTabUrl = '';
const STORAGE_KEY   = 'savedReports';
const MAX_REPORTS   = 20;
const ALL_CATS      = ['performance', 'accessibility', 'best-practices', 'seo'];

/* ── Init ─────────────────────────────── */
async function init() {
  await detectTabUrl();
  await refreshApiStatus();
  await renderRecentHistory();
  bindEvents();

  chrome.tabs.onActivated.addListener(info =>
    chrome.tabs.get(info.tabId).then(applyTabUrl).catch(detectTabUrl)
  );
  chrome.tabs.onUpdated.addListener((id, change, tab) => {
    if (change.status === 'complete') tab ? applyTabUrl(tab) : detectTabUrl();
  });
  chrome.windows.onFocusChanged.addListener(wid => {
    if (wid !== chrome.windows.WINDOW_ID_NONE) detectTabUrl();
  });
  chrome.storage.onChanged.addListener((changes, ns) => {
    if (ns === 'sync' && changes.psiApiKey) refreshApiStatus();
  });
}

/* ── URL ──────────────────────────────── */
async function detectTabUrl() {
  try {
    const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    if (tab) applyTabUrl(tab);
  } catch {
    setUrlField('', 'Could not detect tab');
  }
}

function applyTabUrl(tab) {
  if (!tab?.url) { setUrlField('', 'Could not detect URL'); return; }
  if (tab.url.startsWith('http://') || tab.url.startsWith('https://')) {
    currentTabUrl = tab.url;
    setUrlField(tab.url, '');
  } else {
    currentTabUrl = '';
    setUrlField('', 'Not a web page');
  }
  syncBtn();
}

function setUrlField(value, placeholder) {
  // Only overwrite if the user hasn't manually typed something
  if (!ui.urlInput.dataset.userEdited) {
    ui.urlInput.value = value;
    ui.urlInput.placeholder = placeholder || 'Detecting tab…';
  }
  syncBtn();
}

/* ── API Status ───────────────────────── */
async function refreshApiStatus() {
  try {
    const { psiApiKey } = await chrome.storage.sync.get('psiApiKey');
    const ok = psiApiKey && psiApiKey.length > 10;
    ui.apiKeyStatus.textContent = ok ? 'Configured ✓' : 'Not configured';
    ui.apiKeyStatus.className   = ok ? 'sp-api-badge ready' : 'sp-api-badge missing';
  } catch {
    ui.apiKeyStatus.textContent = 'Unknown';
    ui.apiKeyStatus.className   = 'sp-api-badge missing';
  }
}

/* ── Button enable/disable ────────────── */
function syncBtn() {
  const url = ui.urlInput.value.trim() || currentTabUrl;
  const hasStrat = ui.strategyMobile.checked || ui.strategyDesktop.checked;
  ui.btnGenerate.disabled = !(url && hasStrat);
}

/* ── Events ───────────────────────────── */
function bindEvents() {
  ui.urlInput.addEventListener('input', () => {
    if (ui.urlInput.value.trim()) {
      ui.urlInput.dataset.userEdited = '1';
    } else {
      delete ui.urlInput.dataset.userEdited;
      // Restore tab URL if cleared
      if (currentTabUrl) { ui.urlInput.value = currentTabUrl; }
    }
    syncBtn();
  });

  ui.strategyMobile.addEventListener('change', syncBtn);
  ui.strategyDesktop.addEventListener('change', syncBtn);

  ui.btnGenerate.addEventListener('click', handleGenerate);
  ui.openOptions.addEventListener('click', () => chrome.runtime.openOptionsPage());

  ui.btnHistory.addEventListener('click', showHistoryView);
  ui.btnViewAll.addEventListener('click', showHistoryView);
  ui.btnBackMain.addEventListener('click', showMainView);
}

/* ── Generate ─────────────────────────── */
async function handleGenerate() {
  const url = ui.urlInput.value.trim() || currentTabUrl;
  const strategies = [];
  if (ui.strategyMobile.checked)  strategies.push('mobile');
  if (ui.strategyDesktop.checked) strategies.push('desktop');
  if (!url || !strategies.length) return;

  ui.btnGenerate.disabled = true;
  ui.btnGenerate.textContent = 'Running…';
  setStatus('');

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'RUN_PSI_AUDIT',
      payload: { url, strategies, categories: ALL_CATS },
    });

    if (!response) throw new Error('No response from background script.');

    if (response.success) {
      // V2: AI pipeline — normalize → analyze → generate mode-specific markdown
      let markdown;
      try {
        setStatus('Analysing with AI…', 'info');
        const { summaries } = parseAndRank(response.results);
        const settings      = await loadAISettings();

        if (summaries.length > 0) {
          // Run analysis on the primary (first) summary; multi-strategy merged by parseAndRank
          const analysis = await runAnalysis(summaries[0], settings);
          markdown = generateCombinedMarkdown(analysis, summaries, settings.outputMode);
        } else {
          // Fallback to legacy report-builder if normalization yields nothing
          markdown = generateReport(response.results, response.url);
        }
      } catch (aiErr) {
        console.warn({ error: aiErr }, 'AI analysis failed, falling back to legacy report');
        markdown = generateReport(response.results, response.url);
      }

      const id = await saveReport({
        url: response.url, markdown,
        strategies: response.results.map(r => r.strategy),
        rawResults: response.results,
      });
      await renderRecentHistory();
      if (id) chrome.tabs.create({ url: `report.html?id=${id}` });
    } else {
      handlePSIError(response);
    }
  } catch (err) {
    setStatus(err.message || 'An error occurred', 'error');
  } finally {
    const btn = ui.btnGenerate;
    btn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
      Generate Agent Report`;
    syncBtn();
  }
}

function handlePSIError(response) {
  if (response.error === 'NO_API_KEY') {
    setStatus('No API key — click Manage in Settings', 'error');
  } else {
    setStatus(response.message || 'PSI error', 'error');
  }
}

/* ── Status ───────────────────────────── */
function setStatus(msg, type, clearMs) {
  if (!msg) { ui.statusLine.hidden = true; return; }
  ui.statusLine.textContent = msg;
  ui.statusLine.className   = 'sp-status ' + (type || '');
  ui.statusLine.hidden      = false;
  if (clearMs) setTimeout(() => { ui.statusLine.hidden = true; }, clearMs);
}

/* ── Storage ──────────────────────────── */
async function loadReports() {
  const { [STORAGE_KEY]: list = [] } = await chrome.storage.local.get(STORAGE_KEY);
  return list;
}

async function saveReport({ url, markdown, strategies, rawResults }) {
  const existing = await loadReports();
  const id = Date.now().toString(36) + Math.random().toString(36).slice(2);
  const updated = [{ id, url, markdown, strategies, rawResults, timestamp: Date.now() }, ...existing].slice(0, MAX_REPORTS);
  await chrome.storage.local.set({ [STORAGE_KEY]: updated });
  return id;
}

async function deleteReport(id) {
  const existing = await loadReports();
  await chrome.storage.local.set({ [STORAGE_KEY]: existing.filter(r => r.id !== id) });
}

/* ── Recent history (main view) ───────── */
async function renderRecentHistory() {
  const reports = await loadReports();
  ui.recentList.innerHTML = '';

  if (!reports.length) {
    ui.recentList.innerHTML = '<span class="sp-empty">No reports yet.</span>';
    return;
  }

  reports.slice(0, 3).forEach(r => {
    ui.recentList.appendChild(makeCompactRow(r));
  });
}

function makeCompactRow(r) {
  let host = r.url;
  try { host = new URL(r.url).hostname; } catch {}

  const el = document.createElement('div');
  el.className = 'sp-history-row';
  el.innerHTML = `
    <span class="sp-history-host">${esc(host)}</span>
    <span class="sp-history-when">${relTime(r.timestamp)}</span>`;
  el.addEventListener('click', () => chrome.tabs.create({ url: `report.html?id=${r.id}` }));
  return el;
}

/* ── Views ────────────────────────────── */
function showHistoryView() {
  ui.viewMain.classList.remove('active');
  ui.viewMain.hidden    = true;
  ui.viewHistory.hidden = false;
  ui.viewHistory.classList.add('active');
  renderFullHistory();
}

function showMainView() {
  ui.viewHistory.classList.remove('active');
  ui.viewHistory.hidden = true;
  ui.viewMain.hidden    = false;
  ui.viewMain.classList.add('active');
  detectTabUrl();
  refreshApiStatus();
  renderRecentHistory();
}

/* ── Full history view ────────────────── */
async function renderFullHistory() {
  const reports = await loadReports();
  ui.historyList.innerHTML = '';

  if (!reports.length) {
    ui.historyList.innerHTML = '<span class="sp-empty">No saved reports.</span>';
    return;
  }

  reports.forEach(r => ui.historyList.appendChild(makeFullRow(r)));
}

function makeFullRow(r) {
  const strat = (r.strategies || []).join(' + ').toUpperCase();
  const el = document.createElement('div');
  el.className = 'sp-full-row';
  el.innerHTML = `
    <div class="sp-full-row-top">
      <span class="sp-full-url">${esc(r.url)}</span>
      <span class="sp-strat-badge">${esc(strat)}</span>
    </div>
    <div class="sp-full-row-bot">
      <span class="sp-full-date">${new Date(r.timestamp).toLocaleString()}</span>
      <div class="sp-full-actions">
        <button class="sp-act-btn primary open-btn">Open</button>
        <button class="sp-act-btn dl-btn">Download</button>
        <button class="sp-act-btn danger del-btn">Delete</button>
      </div>
    </div>`;

  el.querySelector('.open-btn').addEventListener('click', e => { e.stopPropagation(); chrome.tabs.create({ url: `report.html?id=${r.id}` }); });
  el.querySelector('.dl-btn').addEventListener('click', e => { e.stopPropagation(); downloadMd(r); });
  el.querySelector('.del-btn').addEventListener('click', async e => {
    e.stopPropagation();
    if (!confirm('Delete this report?')) return;
    await deleteReport(r.id);
    renderFullHistory();
    renderRecentHistory();
  });
  el.addEventListener('click', () => chrome.tabs.create({ url: `report.html?id=${r.id}` }));
  return el;
}

/* ── Helpers ──────────────────────────── */
function downloadMd(r) {
  let host = 'report';
  try { host = new URL(r.url).hostname.replace(/\./g, '-'); } catch {}
  const a = Object.assign(document.createElement('a'), {
    href: URL.createObjectURL(new Blob([r.markdown], { type: 'text/markdown' })),
    download: `pagespeed-${host}.md`,
  });
  a.click();
  URL.revokeObjectURL(a.href);
}

function relTime(ts) {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60)    return 'Just now';
  if (s < 3600)  return `${Math.floor(s/60)}h ago`;
  if (s < 86400) return `${Math.floor(s/3600)}h ago`;
  return `${Math.floor(s/86400)}d ago`;
}

function esc(s) {
  return String(s ?? '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

document.addEventListener('DOMContentLoaded', init);
