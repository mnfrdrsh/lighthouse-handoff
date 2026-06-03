// src/output/exporters.js
// Export Engine — Copy to clipboard and Download as file.

/**
 * Copy text to the clipboard.
 *
 * @param {string} text
 * @returns {Promise<void>}
 */
export async function copyToClipboard(text) {
  if (!navigator.clipboard?.writeText) {
    // Fallback for environments where clipboard API is not available
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.cssText = 'position:fixed;top:-9999px;left:-9999px';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    return;
  }
  await navigator.clipboard.writeText(text);
}

/**
 * Trigger a browser download of text content as a Markdown file.
 *
 * @param {string} content
 * @param {string} [filename='lighthouse-handoff.md']
 * @returns {void}
 */
export function downloadMarkdown(content, filename = 'lighthouse-handoff.md') {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
  const url  = URL.createObjectURL(blob);

  const anchor = Object.assign(document.createElement('a'), {
    href:     url,
    download: filename,
  });

  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

/**
 * Build a safe filename from a URL and output mode.
 *
 * @param {string} pageUrl
 * @param {string} [suffix]
 * @returns {string}
 */
export function buildFilename(pageUrl, suffix = '') {
  let host = 'report';
  try {
    host = new URL(pageUrl).hostname.replace(/\./g, '-');
  } catch { /* ignore invalid URLs */ }

  const parts = ['lighthouse-handoff', host];
  if (suffix) parts.push(suffix);

  return parts.join('_') + '.md';
}
