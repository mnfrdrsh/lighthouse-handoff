// report.js — Lighthouse Handoff Report Viewer (document style)
'use strict';

import { parseMarkdownReport } from './utils/report-parser.js';

const $ = id => document.getElementById(id);

const ui = {
  loading:       $('rpt-loading'),
  error:         $('rpt-error'),
  errorMsg:      $('rpt-error-msg'),
  errBack:       $('btn-error-back'),
  mdView:        $('rpt-markdown'),
  jsonView:      $('rpt-json'),
  tabMd:         $('tab-markdown'),
  tabJson:       $('tab-json'),
  btnCopy:       $('btn-copy-md'),
  btnDl:         $('btn-download-md'),
  btnCopyJson:   $('btn-copy-json'),
  metaUrl:       $('meta-url'),
  metaDate:      $('meta-date'),
  metaStrat:     $('meta-strategies'),
  metaAi:        $('meta-ai'),
  docIssues:     $('doc-issues'),
  docTopActions: $('doc-top-actions'),
  docFixes:      $('doc-fixes'),
  docCriteria:   $('doc-criteria'),
  docGuardrails: $('doc-guardrails'),
  jsonCode:      $('json-code'),
};

let report = null;

/* ── Init ──────────────────────────────────── */
async function init() {
  bindEvents();

  const id = new URLSearchParams(window.location.search).get('id');
  if (!id) { showError('No report ID specified in the URL.'); return; }

  try {
    const { savedReports = [] } = await chrome.storage.local.get('savedReports');
    report = savedReports.find(r => r.id === id) || null;

    if (!report) { showError('Report not found. It may have been deleted.'); return; }

    render(report);
  } catch (err) {
    showError('Failed to load report: ' + err.message);
  }
}

/* ── Events ────────────────────────────────── */
function bindEvents() {
  ui.tabMd.addEventListener('click', () => switchTab('md'));
  ui.tabJson.addEventListener('click', () => switchTab('json'));

  ui.btnCopy.addEventListener('click', () => {
    if (!report) return;
    navigator.clipboard.writeText(report.markdown).then(() => flash(ui.btnCopy, '✓'));
  });

  ui.btnDl.addEventListener('click', () => {
    if (!report) return;
    let host = 'report';
    try { host = new URL(report.url).hostname.replace(/\./g, '-'); } catch {}
    const a = Object.assign(document.createElement('a'), {
      href: URL.createObjectURL(new Blob([report.markdown], { type: 'text/markdown' })),
      download: `pagespeed-${host}.md`,
    });
    a.click();
    URL.revokeObjectURL(a.href);
  });

  ui.btnCopyJson.addEventListener('click', () => {
    if (!report?.rawResults) return;
    navigator.clipboard.writeText(JSON.stringify(report.rawResults, null, 2))
      .then(() => flash(ui.btnCopyJson, 'Copied!'));
  });

  ui.errBack.addEventListener('click', () => window.close());
}

function switchTab(which) {
  const isMd = which === 'md';
  ui.tabMd.classList.toggle('active', isMd);
  ui.tabJson.classList.toggle('active', !isMd);
  ui.mdView.hidden   = !isMd;
  ui.jsonView.hidden = isMd;
}

/* ── Render ────────────────────────────────── */
function render(r) {
  ui.loading.hidden = true;
  ui.mdView.hidden  = false;

  const parsed = parseMarkdownReport(r.markdown);

  // Meta bar
  const displayUrl = parsed?.url || r.url || '—';
  ui.metaUrl.textContent = displayUrl;
  ui.metaUrl.href        = displayUrl;
  ui.metaDate.textContent    = new Date(r.timestamp).toLocaleString();
  ui.metaStrat.textContent   = (r.strategies || []).join(' + ').toUpperCase() || '—';
  ui.metaAi.textContent      = parsed?.aiProvider ? `${parsed.aiProvider} · ${parsed.aiModel}` : '—';

  // Extract Top Agent Actions (take the first instruction line from top 3-5 issues)
  const topActions = (parsed?.issues || []).slice(0, 3).map(i => {
    let action = i.agentInstructions || i.recommendedFix || i.title;
    action = action.split('\n')[0].replace(/^-\s*/, '').trim();
    if (!action.includes(i.title) && action.length > 10) {
      return `**${i.title}**: ${action}`;
    }
    return action;
  });

  // Sections
  renderIssues(parsed?.issues || []);
  renderList(ui.docTopActions, topActions);
  renderList(ui.docFixes,      parsed?.concreteFixes      || []);
  renderList(ui.docCriteria,   parsed?.acceptanceCriteria || []);
  renderList(ui.docGuardrails, parsed?.guardrails         || []);

  // JSON
  if (r.rawResults) {
    ui.jsonCode.textContent = JSON.stringify(r.rawResults, null, 2);
  } else {
    ui.jsonCode.textContent = 'No raw PSI data saved for this report.';
  }
}

/* ── Issues list (with severity badges) ─────── */
function renderIssues(issues) {
  ui.docIssues.innerHTML = '';

  if (!issues.length) {
    appendEmptyNote(ui.docIssues, 'No high-impact issues detected.');
    return;
  }

  issues.forEach(issue => {
    const li  = document.createElement('li');
    const sev = (issue.impact || 'medium').toLowerCase();

    li.innerHTML = `
      <span class="doc-bullet">•</span>
      <span class="doc-item-text">
        ${esc(issue.title)}
        <span class="sev-badge ${sev}">${esc(issue.impact)}</span>
      </span>`;

    ui.docIssues.appendChild(li);
  });
}

/* ── Generic bullet list ──────────────────── */
function renderList(container, items) {
  container.innerHTML = '';

  if (!items.length) {
    appendEmptyNote(container, 'None.');
    return;
  }

  items.forEach(text => {
    const li = document.createElement('li');
    li.innerHTML = `<span class="doc-bullet">•</span><span class="doc-item-text">${renderInline(esc(text))}</span>`;
    container.appendChild(li);
  });
}

/* ── Inline rendering ─────────────────────── */
function renderInline(html) {
  // Wrap `backtick` spans
  return html.replace(/`([^`]+)`/g, '<code class="doc-code">$1</code>');
}

function appendEmptyNote(container, msg) {
  const li = document.createElement('li');
  li.innerHTML = `<span class="doc-bullet">•</span><span class="doc-item-text" style="color:var(--muted)">${msg}</span>`;
  container.appendChild(li);
}

/* ── Error ────────────────────────────────── */
function showError(msg) {
  ui.loading.hidden  = true;
  ui.mdView.hidden   = true;
  ui.jsonView.hidden = true;
  ui.error.hidden    = false;
  ui.errorMsg.textContent = msg;
}

/* ── Helpers ──────────────────────────────── */
function esc(s) {
  return String(s ?? '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function flash(btn, text) {
  const orig = btn.innerHTML;
  btn.textContent = text;
  setTimeout(() => { btn.innerHTML = orig; }, 1400);
}

document.addEventListener('DOMContentLoaded', init);
