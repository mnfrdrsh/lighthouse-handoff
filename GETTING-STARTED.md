# Getting Started with Lighthouse Handoff

This guide helps **anyone** get the extension running and generating useful reports.

> **Note**: The extension is designed for end-users who provide their own free Google PageSpeed Insights API key. No data is sent to the extension author.

---

## 1. Create a Google PageSpeed Insights API Key

This is the most important step.

### Step-by-step Instructions

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)

2. **Create a new project** (recommended for isolation):
   - Click the project dropdown at the top
   - Click **New Project**
   - Name it something like `Lighthouse Handoff` or `PSI Testing`
   - Click **Create**

3. **Enable the PageSpeed Insights API**:
   - Make sure your new project is selected
   - Go to **APIs & Services > Library**
   - Search for "**PageSpeed Insights API**"
   - Click on it → Click **Enable**

4. **Create an API Key**:
   - Go to **APIs & Services > Credentials**
   - Click **+ Create Credentials** → **API key**
   - Google will generate a key (it will look like `AIzaSy...`)

5. **Restrict the API Key (Important!)**:
   - Click on the newly created key to edit it
   - Under **Application restrictions**, choose **None** (for now) or **IP addresses** if you want to lock it down
   - Under **API restrictions**, select **Restrict key**
   - Choose **PageSpeed Insights API** only
   - Click **Save**

   **Why restrict it?**  
   This prevents the key from being abused if it ever leaks. You should **never** use an unrestricted key in production.

6. **Copy the key** — you’ll need it in the next section.

> **Note**: The free tier of PageSpeed Insights is quite generous, but heavy testing can still hit rate limits. One key is usually enough for personal testing.

---

## 2. Install the Extension

### From the Chrome Web Store (recommended when published)
Search for "Lighthouse Handoff" and click **Add to Chrome**.

### Sideload (works today)
1. Download the latest release `.zip` (or clone this repo) and unzip it to a permanent location.
2. Go to `chrome://extensions`.
3. Enable **Developer mode** (top right).
4. Click **Load unpacked** and select the folder that contains `manifest.json`.

You should see the **Lighthouse Handoff** icon (lighthouse on a document) in your toolbar.

**Pin it**:
- Click the puzzle icon in the toolbar → pin "Lighthouse Handoff".

The UI is a **side panel** that stays open when you switch tabs.

---

## 3. Add Your Free PSI API Key

1. Open the side panel (click the extension icon).
2. Click **Manage** (or the Options link) if it says the key is not set.
3. Paste your Google PageSpeed Insights API key and save.

The panel will show **"Configured ✓"**.

**Quick way to get a key** (2–3 minutes):
- [Google Cloud Console](https://console.cloud.google.com/)
- New project → enable **PageSpeed Insights API**
- Create **API Key** → restrict it to the PSI API only
- Copy and paste into the extension

See the detailed "How to Get a PSI API Key" section in the main README or GETTING-STARTED for screenshots.

---

## 4. Generate Your First Report

1. Go to any public web page.
2. Open the side panel.
3. It auto-detects the current tab URL (or paste one manually).
4. Choose strategies (Mobile + Desktop recommended) and categories.
5. Click **Generate Agent Report**.
6. When finished, click **Download .md** (or browse previous reports in the **History** view inside the panel).

The generated report is designed to be dropped straight into a coding agent.

---

## 5. Useful Tips

- Start with **Mobile + Desktop + Performance** only for the first runs.
- Use the in-panel **History** to keep and compare reports without filling your Downloads folder.
- The reports include concrete resources (specific images/scripts with sizes) and DevTools steps — point your agent at the .md file.
- Re-run the report after making changes to see the before/after.

---

## 6. Troubleshooting

- "No API key set" or generation fails with NO_API_KEY → Go to Options and save a valid restricted PSI key.
- Rate limit (429) → Wait 60–120 seconds or create a second restricted key in another Google Cloud project.
- URL not updating when switching tabs → Click the ↻ refresh button next to the URL field.
- Extension doesn't appear after sideload → Make sure you selected the folder containing `manifest.json`.

---

## 7. Privacy & Security

- Your key and reports are stored **only locally** in your browser.
- Only the URLs *you* choose are sent to Google's PSI API (using *your* key).
- Full details: see [PRIVACY.md](./PRIVACY.md) in the extension folder.

Never share your API key.

---

## 8. For Developers & Contributors

See the main [README.md](./README.md) for architecture and the original [IMPLEMENTATION-PLAN.md](./IMPLEMENTATION-PLAN.md).

This is a pure Manifest V3 extension with no build step. Report generation is 100% client-side.

---

## 8. Security Reminders

- Never commit your API key to git
- Never share the key publicly
- Keep the key restricted to only the PageSpeed Insights API
- You can delete and recreate the key at any time in the Google Cloud Console

---

## Quick Checklist for New Users

- [ ] Installed the extension (Web Store or Load unpacked)
- [ ] Created a restricted Google PageSpeed Insights API key
- [ ] Saved the key in the extension Options (side panel → Manage)
- [ ] Generated at least one report on a real site
- [ ] Reviewed the report in the History view or as a downloaded .md

---

## What's Next?

- Use the reports directly with your favorite coding agent.
- Re-run after making changes to see the difference.
- Explore the in-panel History to manage previous reports.

If you run into problems or have ideas for better reports, open an issue on the repository: https://github.com/mnfrdrsh/lighthouse-handoff

**Enjoy turning slow pages into actionable agent briefs!**