# Privacy Policy for Lighthouse Handoff

**Last updated**: 2026-06-02

Lighthouse Handoff is a free, open-source Chrome extension that converts Google PageSpeed Insights (PSI) / Lighthouse audit data into structured, coding-agent-ready Markdown reports.

## Data We Collect and Store

- **API Key**: You provide your own Google PageSpeed Insights API key (free from Google Cloud Console). It is stored **only locally** in your browser using `chrome.storage.sync` (synced across your devices if you are signed into Chrome). It is **never sent to us or any third party** except directly to `https://www.googleapis.com/pagespeedonline/v5/runPagespeed` when you click "Generate".

- **Reports**: Generated Markdown reports are stored **only locally** in your browser using `chrome.storage.local`. You can view, copy, download, or delete them at any time from the extension's History view. They are **never uploaded or sent anywhere**.

- **URLs you audit**: Only the URL(s) you explicitly choose to audit are sent to Google's PageSpeed Insights API (using *your* API key). No browsing history is collected or sent by the extension.

- **No analytics, tracking, or telemetry**: We do not collect any usage data, crash reports, or personal information.

## Data Sent to Third Parties

The *only* network requests made by the extension (besides loading its own files) are:

- Calls to Google's PageSpeed Insights API (`https://www.googleapis.com/pagespeedonline/v5/runPagespeed`) to fetch audit data for a URL *you* chose, authenticated with *your* API key.

We have no control over, and are not responsible for, Google's privacy practices. See Google's [Privacy Policy](https://policies.google.com/privacy).

## Permissions Explained

- `activeTab`, `tabs`: To detect the URL of the currently active tab (so you don't have to copy-paste).
- `storage`: To save your API key and your generated reports locally.
- `sidePanel`: To provide the main user interface as a persistent side panel.
- `host_permissions` for `https://www.googleapis.com/*`: To call the PageSpeed Insights API on your behalf using your key.

## Your Rights

- You can delete your API key and all stored reports at any time from the Options page and History view.
- All data stays on your device / in your Chrome profile.

## Contact

This is a solo / small project. For questions or issues, open an issue on the GitHub repository (link to be added on first public release) or contact the developer.

## Changes to This Policy

We may update this policy as the extension evolves. Material changes will be noted in the extension or repository.

---

*This extension is provided as-is. You are responsible for obtaining and managing your own Google API key and complying with Google's terms of service.*
