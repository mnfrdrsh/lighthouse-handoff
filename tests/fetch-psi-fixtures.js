// tests/fetch-psi-fixtures.js
// Fetches real PSI data for test URLs and saves JSON fixtures.
// Run with: node tests/fetch-psi-fixtures.js
//
// Uses the public PSI API demo endpoint (no key required).
// Rate-limited: sequential fetches with delay between requests.

import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dir = dirname(fileURLToPath(import.meta.url));

const FIXTURE_DIR      = join(__dir, 'fixtures', 'sample-psi');
const SNAPSHOT_DIR     = join(__dir, 'output-snapshots');

// Create dirs
[FIXTURE_DIR, SNAPSHOT_DIR].forEach(d => mkdirSync(d, { recursive: true }));

const PSI_BASE = 'https://www.googleapis.com/pagespeedonline/v5/runPagespeed';

// Test matrix: label → { url, strategies }
const TESTS = [
  { label: 'gov-uk',            url: 'https://www.gov.uk/',                  strategies: ['mobile', 'desktop'], tag: 'high-performing'   },
  { label: 'wikipedia',         url: 'https://www.wikipedia.org/',            strategies: ['mobile', 'desktop'], tag: 'average'           },
  { label: 'cnn',               url: 'https://www.cnn.com/',                  strategies: ['mobile', 'desktop'], tag: 'poor-performing'   },
  { label: 'national-geographic',url: 'https://www.nationalgeographic.com/', strategies: ['mobile'],            tag: 'image-heavy'       },
  { label: 'airbnb',            url: 'https://www.airbnb.com/',               strategies: ['mobile'],            tag: 'js-heavy'          },
];

const CATEGORIES = 'performance,accessibility,best-practices,seo';
const DELAY_MS   = 3500; // polite delay between requests

async function fetchPSI(url, strategy) {
  const apiKey = process.env.PSI_API_KEY;
  if (!apiKey) throw new Error('PSI_API_KEY environment variable is required. Set it before running this script.');

  // PSI v5 requires repeated `category` params with uppercase enum names
  const params = new URLSearchParams({ url, strategy, key: apiKey });
  for (const cat of ['PERFORMANCE', 'ACCESSIBILITY', 'BEST_PRACTICES', 'SEO']) {
    params.append('category', cat);
  }
  const endpoint = `${PSI_BASE}?${params}`;
  const res = await fetch(endpoint);
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`PSI API returned ${res.status} for ${url} (${strategy}): ${text.slice(0, 200)}`);
  }
  return res.json();
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function main() {
  console.log('=== Fetching PSI Fixtures ===\n');

  const manifest = {};

  for (const test of TESTS) {
    const results = [];

    for (const strategy of test.strategies) {
      const key = `${test.label}-${strategy}`;
      console.log(`Fetching: ${test.url} [${strategy}]...`);

      try {
        const data = await fetchPSI(test.url, strategy);
        const filename = `${key}.json`;
        const filepath = join(FIXTURE_DIR, filename);

        // Save full PSI response
        writeFileSync(filepath, JSON.stringify(data, null, 2), 'utf8');
        console.log(`  ✓ Saved ${filename}`);

        results.push({ strategy, success: true, data, filename });
      } catch (err) {
        console.error(`  ✗ Failed: ${err.message}`);
        results.push({ strategy, success: false, error: err.message });
      }

      // Rate limit courtesy
      await sleep(DELAY_MS);
    }

    manifest[test.label] = {
      url:       test.url,
      tag:       test.tag,
      strategies: test.strategies,
      results: results.map(r => ({ strategy: r.strategy, success: r.success, filename: r.filename })),
    };
  }

  // Save manifest
  writeFileSync(
    join(FIXTURE_DIR, 'manifest.json'),
    JSON.stringify(manifest, null, 2),
    'utf8'
  );
  console.log('\n✓ Saved manifest.json');
  console.log('\nDone! Run tests/run-pipeline.js next to generate output snapshots.');
}

main().catch(err => {
  console.error('Fatal:', err);
  process.exit(1);
});
