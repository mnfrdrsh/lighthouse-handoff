# Lighthouse Handoff

> Turn PageSpeed Insights reports into coding-agent-ready Markdown briefs.

**Lighthouse Handoff** is a free Chrome extension that takes a URL, runs Google PageSpeed Insights (Lighthouse), and turns the raw data into a clean, prioritized, **actionable Markdown report** designed specifically for coding agents (Cursor, Claude, Grok, Copilot, etc.).

Instead of "Lighthouse says X is bad", you get:
- Prioritized, high-impact issues
- Concrete next steps with DevTools guidance
- Clear acceptance criteria
- Strong guardrails so the agent doesn't break your site

---

## Install (for anyone)

### Recommended: Chrome Web Store (coming soon)
(Once published, search for "Lighthouse Handoff" or visit the store link.)

**Source & issues**: https://github.com/mnfrdrsh/lighthouse-handoff

### Sideload (works today)
1. Download the latest `.zip` from the Releases page on the repository (or clone this repo).
2. Go to `chrome://extensions` in Chrome.
3. Enable **Developer mode** (top right).
4. Click **Load unpacked** and select the extracted folder.

The extension icon (lighthouse + document) will appear in your toolbar. It opens as a **side panel** (stays open while you browse).

---

## Quick Start

1. Click the extension icon → side panel opens.
2. It auto-detects the current tab's URL (or paste one).
3. Choose strategies (Mobile + Desktop recommended) and categories (start with Performance).
4. Enter your free Google PageSpeed Insights API key in **Options** (one-time).
5. Click **Generate Agent Report**.
6. View/download the `.md` report (also saved in **History** inside the panel).

Use the report directly as context for your coding agent.

---

## Getting a Free PSI API Key (required)

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create/select a project → enable the **PageSpeed Insights API**.
3. Create an **API Key** (restrict it to the PSI API only — strongly recommended).
4. Paste the key into the extension's **Options** page (click "Manage" in the side panel).

Your key is stored only locally in your browser. The extension sends *only* the URL you choose to Google's API using *your* key.

Full step-by-step in [GETTING-STARTED.md](./GETTING-STARTED.md).

---

## Features

- **Side panel UI** — stays open while you switch tabs.
- **Auto URL detection** + manual override.
- **Local history** — all reports saved in the extension (view, copy, re-download, delete).
- **Self-contained reports** — includes concrete resource lists (e.g. specific heavy images/scripts) + precise DevTools steps so a coding agent can act without the original Lighthouse data.
- **Strong guardrails** built into every report.
- **No tracking** — everything stays local except the PSI API calls you explicitly trigger.

---

## Privacy

- Your API key and reports live only in your browser (`chrome.storage`).
- Only the URLs *you* choose to audit are sent to Google (authenticated with *your* key).
- Full details: [PRIVACY.md](./PRIVACY.md).

---

## For Developers / Power Users

- Pure Manifest V3, no build step.
- All report logic client-side in `report-builder.js`.
- See [IMPLEMENTATION-PLAN.md](./IMPLEMENTATION-PLAN.md) for the original vision and [TASKS.md](./TASKS.md) for current status.
- Source: the folder you loaded (or the repo when published).

---

## Roadmap / Status

This is currently at a usable MVP stage (side panel, local history, high-quality reports, your own free API key).

Future ideas (community welcome):
- Even smarter extraction from PSI details.
- Optional "brutal" vs "friendly" report styles.
- Firefox support.
- Chrome Web Store publication.

---

## Contributing

Feedback, bug reports, and PRs are welcome! Open an issue or pull request on the repository: https://github.com/mnfrdrsh/lighthouse-handoff

---

## License

MIT (to be confirmed on first release).

---

**Made for people who hand real work to coding agents and want the reports to actually be useful.**

---

## Distribution & Publishing (for the maintainer)

To produce a clean package for others:

```powershell
# From the project root
.\pack-extension.ps1
```

This creates `dist\lighthouse-handoff.zip`.

### For "Load unpacked" users
- Unzip the archive.
- Point `chrome://extensions` → Load unpacked at the unzipped folder.

### For Chrome Web Store
1. Upload the zip via the developer dashboard.
2. You will still need to provide:
   - Screenshots (take them from a loaded copy — 1280×800 or 640×400 recommended).
   - Store listing description (copy/adapt from this README).
   - Privacy policy link (host `PRIVACY.md` somewhere public or use the one in the repo).
   - Icon (we already have 128×128 in the package).
3. Request the "tabs", "activeTab", "storage", and "sidePanel" permissions in the store listing (they are justified by the feature set).

See `pack-extension.ps1` comments and `PRIVACY.md` for more details.

Once published, update the install instructions in this README and GETTING-STARTED.md with the real store link.
