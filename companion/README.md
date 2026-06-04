# Liquid Local Companion (Mock)

This is a plain Node.js mock server designed to test the extension-to-localhost communication path for the Lighthouse Handoff `liquid-local` provider.

## Purpose

Before implementing the real Liquid LEAP SDK (which requires downloading and running an actual LLM on-device), this mock server proves that:
1. The Chrome extension can correctly send data to `http://localhost:31313`.
2. The payload contract is correct.
3. The returned JSON is correctly ingested by the extension and rendered into Markdown.

## Usage

Start the server:
```bash
npm install
npm start
```

In the Chrome extension:
1. Open the Lighthouse Handoff options page.
2. Select **Liquid Local Companion (experimental)**.
3. The status should immediately change to `Liquid Companion: Connected (Model: mock-lfm2.5-350m)`.
4. Run an analysis. The generated Markdown will contain mock instructions proving the connection worked.
