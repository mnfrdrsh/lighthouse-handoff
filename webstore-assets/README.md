# Web Store Assets — Lighthouse Handoff

This folder holds the visual assets required for publishing to the Chrome Web Store.

## Required Assets (upload in the developer dashboard)

1. **Screenshots** (1 or more, up to 5 recommended)
   - Recommended size: **1280 × 800** pixels (or 640 × 400)
   - Format: PNG or JPG
   - Content ideas (take real screenshots from the loaded extension):
     - Side panel open on a typical marketing / e-commerce page, showing the Generate form with URL detected + checkboxes.
     - Side panel with "Report ready" preview after generation.
     - History view showing one or two saved reports.
     - A sample of the generated Markdown report open in a code editor / Cursor / Claude (shows the value).
   - Keep branding consistent (use the lighthouse+doc icon colors if possible).
   - Do **not** include any personal URLs or keys in screenshots.

2. **Promotional tile** (small promo image)
   - Exact size: **440 × 280** pixels
   - Shown in the store listing and search results.
   - Should convey "PageSpeed → clean actionable MD for agents" quickly.
   - Text overlay optional: "Lighthouse Handoff" + "Agent-ready reports from PSI".

3. **Optional large promo tile**
   - 920 × 680 or 1400 × 560 (marquee)
   - Not strictly required for initial listing.

4. **Icon**
   - We already provide 128×128 in the package (icons/icon-128.png).
   - The store will also let you upload higher-res if desired.

## How to Capture Good Screenshots (Windows + Chrome)

1. Load the unpacked extension (or the unzipped dist folder).
2. Pin the extension.
3. Open a representative public page (e.g. a news site, docs site, or your own marketing page).
4. Click the toolbar icon → side panel opens on the right.
5. For best store visuals:
   - Use a clean Chrome profile (no other extensions pinned, default theme).
   - Set your browser window to ~1400–1600 px wide so the side panel + page content both look good.
   - Take full screenshots with Snipping Tool / ShareX / built-in Win+Shift+S.
   - Crop / resize precisely to 1280x800 using any editor (or PowerToys Image Resizer).
6. For the report demo shot: generate a real report, then copy the .md content into a nice editor window and screenshot that (shows the "value" to potential users).

## Placeholder Files (replace before submitting)

- `screenshot-1280x800-placeholder.png` (or .jpg)
- `promo-440x280.png`

The current placeholders (if present) are AI-generated stand-ins. **Replace them with actual captures of the real UI + real reports** before you submit to the store. Real screenshots convert much better.

## Store Listing Text (copy/adapt from root README.md)

Short description (132 chars max for store):
"Turn any page's PageSpeed Insights report into a clean, prioritized, coding-agent-ready Markdown brief. Self-contained with concrete fixes + DevTools steps."

Detailed description: use the Features + Quick Start sections from README.md.

## Privacy Policy

Link to the hosted PRIVACY.md (you can host it on GitHub Pages, your site, or use the raw GitHub URL of PRIVACY.md once the repo is public).

## After First Publish

- Update the "Install" section in README.md and GETTING-STARTED.md with the real Chrome Web Store link.
- Add the store badge / link to this folder or root README if desired.

---

**Tip**: You can run `.\pack-extension.ps1` again after any changes to the core files; webstore-assets is intentionally kept out of the runtime zip.

Good luck with the listing! The combination of side panel persistence + history + genuinely actionable self-contained reports is the differentiator.
