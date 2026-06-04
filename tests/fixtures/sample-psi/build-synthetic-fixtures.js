// tests/fixtures/sample-psi/build-synthetic-fixtures.js
// Generates realistic PSI-shaped JSON fixtures from known real-world Lighthouse data.
// Run with: node tests/fixtures/sample-psi/build-synthetic-fixtures.js
//
// Sources: publicly documented Lighthouse scores, Web Almanac, CrUX data.
// Structures match the real PSI v5 API lighthouseResult shape.

import { writeFileSync, mkdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dir = dirname(fileURLToPath(import.meta.url));
mkdirSync(__dir, { recursive: true });

// ---------------------------------------------------------------------------
// Helper builders
// ---------------------------------------------------------------------------

function makeCategory(score, auditRefs) {
  return { score, auditRefs };
}

function makeAudit(id, title, description, score, numericValue, displayValue, details = null) {
  const audit = { id, title, description, score, numericValue, displayValue };
  if (details) audit.details = details;
  return audit;
}

function makeTableDetails(items) {
  return { type: 'table', items };
}

function makeLHR(url, strategy, categories, audits, runtimeError = null) {
  return {
    lighthouseResult: {
      finalUrl:     url,
      requestedUrl: url,
      fetchTime:    new Date().toISOString(),
      runtimeError,
      categories,
      audits,
    },
    analysisUTCTimestamp: new Date().toISOString(),
  };
}

// ---------------------------------------------------------------------------
// 1. HIGH PERFORMING: gov.uk (known to be very well optimised)
//    Typical scores: mobile perf ~85, desktop ~97
// ---------------------------------------------------------------------------

function buildGovUkMobile() {
  const categories = {
    performance: makeCategory(0.85, [
      { id: 'largest-contentful-paint',    weight: 10 },
      { id: 'cumulative-layout-shift',     weight: 15 },
      { id: 'interaction-to-next-paint',   weight: 10 },
      { id: 'total-blocking-time',         weight: 30 },
      { id: 'speed-index',                 weight: 10 },
      { id: 'first-contentful-paint',      weight: 10 },
      { id: 'uses-long-cache-ttl',         weight: 0  },
      { id: 'unused-javascript',           weight: 0  },
    ]),
    accessibility:    makeCategory(0.97, []),
    seo:              makeCategory(0.92, []),
    'best-practices': makeCategory(0.95, []),
  };

  const audits = {
    'largest-contentful-paint': makeAudit(
      'largest-contentful-paint', 'Largest Contentful Paint',
      'LCP marks the time at which the largest text or image is rendered.',
      0.72, 2800, '2.8 s'
    ),
    'cumulative-layout-shift': makeAudit(
      'cumulative-layout-shift', 'Cumulative Layout Shift',
      'CLS measures the sum of all unexpected layout shift scores.',
      0.95, 0.02, '0.02'
    ),
    'interaction-to-next-paint': makeAudit(
      'interaction-to-next-paint', 'Interaction to Next Paint',
      'INP measures responsiveness.',
      null, 80, '80 ms'
    ),
    'total-blocking-time': makeAudit(
      'total-blocking-time', 'Total Blocking Time',
      'TBT is the total time blocked by long tasks.',
      0.72, 320, '320 ms'
    ),
    'speed-index': makeAudit(
      'speed-index', 'Speed Index',
      'Speed Index shows how quickly the contents of a page are visibly populated.',
      0.83, 2100, '2.1 s'
    ),
    'first-contentful-paint': makeAudit(
      'first-contentful-paint', 'First Contentful Paint',
      'FCP marks the time at which the first text or image is rendered.',
      0.80, 1800, '1.8 s'
    ),
    'uses-long-cache-ttl': makeAudit(
      'uses-long-cache-ttl', 'Serve static assets with an efficient cache policy',
      'Long cache lifetimes can speed up repeat visits.',
      0.5, null, '3 resources found',
      makeTableDetails([
        { url: 'https://www.gov.uk/assets/static/govuk-frontend-5.js', totalBytes: 45230, wastedBytes: 0 },
        { url: 'https://www.gov.uk/assets/static/main.css',           totalBytes: 28400, wastedBytes: 0 },
      ])
    ),
    'unused-javascript': makeAudit(
      'unused-javascript', 'Remove unused JavaScript',
      'Reduce unused JavaScript to decrease bytes consumed by network activity.',
      0.72, 380, '0.38 s potential savings',
      makeTableDetails([
        { url: 'https://www.gov.uk/assets/static/govuk-frontend-5.js', wastedBytes: 18400, wastedMs: 120 },
      ])
    ),
  };

  return makeLHR('https://www.gov.uk/', 'mobile', categories, audits);
}

function buildGovUkDesktop() {
  const categories = {
    performance: makeCategory(0.97, [
      { id: 'largest-contentful-paint',  weight: 10 },
      { id: 'cumulative-layout-shift',   weight: 15 },
      { id: 'total-blocking-time',       weight: 30 },
      { id: 'speed-index',               weight: 10 },
      { id: 'first-contentful-paint',    weight: 10 },
    ]),
    accessibility:    makeCategory(0.97, []),
    seo:              makeCategory(0.92, []),
    'best-practices': makeCategory(0.95, []),
  };

  const audits = {
    'largest-contentful-paint': makeAudit(
      'largest-contentful-paint', 'Largest Contentful Paint',
      'LCP marks the time at which the largest text or image is rendered.',
      0.99, 980, '0.98 s'
    ),
    'cumulative-layout-shift': makeAudit(
      'cumulative-layout-shift', 'Cumulative Layout Shift',
      'CLS measures visual instability.',
      1.0, 0.01, '0.01'
    ),
    'total-blocking-time': makeAudit(
      'total-blocking-time', 'Total Blocking Time',
      'TBT.',
      0.99, 40, '40 ms'
    ),
    'speed-index': makeAudit(
      'speed-index', 'Speed Index',
      'Speed Index.',
      0.99, 780, '0.78 s'
    ),
    'first-contentful-paint': makeAudit(
      'first-contentful-paint', 'First Contentful Paint',
      'FCP.',
      0.99, 600, '0.60 s'
    ),
  };

  return makeLHR('https://www.gov.uk/', 'desktop', categories, audits);
}

// ---------------------------------------------------------------------------
// 2. AVERAGE: Wikipedia (generally decent but has some issues)
//    Typical: mobile perf ~58, desktop ~82
// ---------------------------------------------------------------------------

function buildWikipediaMobile() {
  const categories = {
    performance: makeCategory(0.58, [
      { id: 'largest-contentful-paint',          weight: 10 },
      { id: 'cumulative-layout-shift',           weight: 15 },
      { id: 'total-blocking-time',               weight: 30 },
      { id: 'render-blocking-resources',         weight: 0  },
      { id: 'unused-javascript',                 weight: 0  },
      { id: 'unused-css-rules',                  weight: 0  },
      { id: 'uses-optimized-images',             weight: 0  },
      { id: 'uses-responsive-images',            weight: 0  },
    ]),
    accessibility:    makeCategory(0.81, [
      { id: 'image-alt', weight: 3 },
      { id: 'label',     weight: 3 },
    ]),
    seo:              makeCategory(0.90, []),
    'best-practices': makeCategory(0.83, []),
  };

  const audits = {
    'largest-contentful-paint': makeAudit(
      'largest-contentful-paint', 'Largest Contentful Paint',
      'LCP marks the time at which the largest text or image is rendered.',
      0.40, 4100, '4.1 s'
    ),
    'cumulative-layout-shift': makeAudit(
      'cumulative-layout-shift', 'Cumulative Layout Shift',
      'CLS measures visual instability.',
      0.80, 0.07, '0.07'
    ),
    'total-blocking-time': makeAudit(
      'total-blocking-time', 'Total Blocking Time',
      'TBT.',
      0.53, 550, '550 ms'
    ),
    'render-blocking-resources': makeAudit(
      'render-blocking-resources', 'Eliminate render-blocking resources',
      'Resources are blocking the first paint of your page.',
      0.5, 820, 'Potential savings of 0.82 s',
      makeTableDetails([
        { url: 'https://en.wikipedia.org/w/load.php?modules=startup', wastedMs: 650, totalBytes: 12400 },
        { url: 'https://en.wikipedia.org/w/load.php?modules=site.styles', wastedMs: 170, totalBytes: 8200 },
      ])
    ),
    'unused-javascript': makeAudit(
      'unused-javascript', 'Remove unused JavaScript',
      'Reduce unused JavaScript.',
      0.55, 480, '0.48 s potential savings',
      makeTableDetails([
        { url: 'https://en.wikipedia.org/w/load.php?modules=mediawiki.legacy.wikibits', wastedBytes: 62000, wastedMs: 280 },
        { url: 'https://en.wikipedia.org/w/load.php?modules=ext.gadget.ReferenceTooltips', wastedBytes: 18400, wastedMs: 120 },
      ])
    ),
    'unused-css-rules': makeAudit(
      'unused-css-rules', 'Remove unused CSS',
      'Remove dead rules from stylesheets.',
      0.5, null, '98 KiB potential savings',
      makeTableDetails([
        { url: 'https://en.wikipedia.org/w/load.php?modules=mediawiki.skinning.content', wastedBytes: 85000 },
        { url: 'https://en.wikipedia.org/w/load.php?modules=ext.cite.styles',            wastedBytes: 13000 },
      ])
    ),
    'uses-optimized-images': makeAudit(
      'uses-optimized-images', 'Efficiently encode images',
      'Optimized images load faster and consume less cellular data.',
      0.6, null, '42 KiB potential savings',
      makeTableDetails([
        { url: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Wikipedia-logo-v2.svg/800px.png', wastedBytes: 32000, totalBytes: 58000 },
      ])
    ),
    'uses-responsive-images': makeAudit(
      'uses-responsive-images', 'Properly size images',
      'Serve images that are appropriately-sized to save cellular data.',
      0.6, null, '28 KiB potential savings',
      makeTableDetails([
        { url: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Wikipedia-logo-v2.svg/800px.png', wastedBytes: 28000, totalBytes: 58000 },
      ])
    ),
    'image-alt': makeAudit(
      'image-alt', 'Image elements have [alt] attributes',
      'Informative elements should aim for short, descriptive alternate text.',
      0.0, null, null,
      makeTableDetails([
        { node: { nodeLabel: '<img class="mw-logo-icon" src="/static/images/icons/wikipedia.png">' } },
      ])
    ),
    'label': makeAudit(
      'label', 'Form elements have associated labels',
      'Labels ensure that form controls are announced properly by screen readers.',
      0.5, null, null,
      makeTableDetails([
        { node: { nodeLabel: '<input type="search" name="search" class="cdx-search-input__input">' } },
      ])
    ),
  };

  return makeLHR('https://www.wikipedia.org/', 'mobile', categories, audits);
}

// ---------------------------------------------------------------------------
// 3. POOR PERFORMING: CNN (known for heavy JS, ads, lots of third-parties)
//    Typical: mobile perf ~18–28, desktop ~45–55
// ---------------------------------------------------------------------------

function buildCnnMobile() {
  const categories = {
    performance: makeCategory(0.22, [
      { id: 'largest-contentful-paint',          weight: 10 },
      { id: 'cumulative-layout-shift',           weight: 15 },
      { id: 'total-blocking-time',               weight: 30 },
      { id: 'unused-javascript',                 weight: 0  },
      { id: 'render-blocking-resources',         weight: 0  },
      { id: 'third-party-summary',               weight: 0  },
      { id: 'uses-optimized-images',             weight: 0  },
      { id: 'modern-image-formats',              weight: 0  },
      { id: 'uses-text-compression',             weight: 0  },
      { id: 'dom-size',                          weight: 0  },
      { id: 'server-response-time',              weight: 0  },
    ]),
    accessibility:    makeCategory(0.74, [
      { id: 'color-contrast',             weight: 3 },
      { id: 'image-alt',                  weight: 3 },
      { id: 'label-content-name-mismatch', weight: 3 },
    ]),
    seo:              makeCategory(0.84, []),
    'best-practices': makeCategory(0.75, []),
  };

  const audits = {
    'largest-contentful-paint': makeAudit(
      'largest-contentful-paint', 'Largest Contentful Paint',
      'LCP marks the time at which the largest text or image is rendered.',
      0.0, 8400, '8.4 s'
    ),
    'cumulative-layout-shift': makeAudit(
      'cumulative-layout-shift', 'Cumulative Layout Shift',
      'CLS measures visual instability.',
      0.1, 0.38, '0.38'
    ),
    'total-blocking-time': makeAudit(
      'total-blocking-time', 'Total Blocking Time',
      'TBT.',
      0.0, 4200, '4,200 ms'
    ),
    'unused-javascript': makeAudit(
      'unused-javascript', 'Remove unused JavaScript',
      'Reduce unused JavaScript.',
      0.0, 3800, '3.8 s potential savings',
      makeTableDetails([
        { url: 'https://www.cnn.com/_assets/1.2.3/en-us/static/js/main.chunk.js',          wastedBytes: 185000, wastedMs: 1200 },
        { url: 'https://sb.scorecardresearch.com/c2/plugins/streamsense.plugin.js',          wastedBytes: 92000,  wastedMs: 580  },
        { url: 'https://cdn.krxd.net/ctjs/controltag.js',                                   wastedBytes: 78000,  wastedMs: 490  },
        { url: 'https://www.googletagmanager.com/gtag/js?id=UA-XXXXXXXX-1',                 wastedBytes: 65000,  wastedMs: 400  },
        { url: 'https://cdn.optimizely.com/js/XXXXXXXXX.js',                                wastedBytes: 55000,  wastedMs: 340  },
        { url: 'https://www.cnn.com/_assets/1.2.3/en-us/static/js/vendor-react.chunk.js',  wastedBytes: 48000,  wastedMs: 300  },
      ])
    ),
    'render-blocking-resources': makeAudit(
      'render-blocking-resources', 'Eliminate render-blocking resources',
      'Resources are blocking the first paint of your page.',
      0.0, 1900, 'Potential savings of 1.9 s',
      makeTableDetails([
        { url: 'https://www.cnn.com/_assets/1.2.3/en-us/static/css/main.css',  wastedMs: 1100, totalBytes: 145000 },
        { url: 'https://www.cnn.com/_assets/1.2.3/en-us/static/css/fonts.css', wastedMs:  800, totalBytes:  28000 },
      ])
    ),
    'third-party-summary': makeAudit(
      'third-party-summary', 'Reduce the impact of third-party code',
      'Third-party code can significantly impact load performance.',
      0.0, null, '32 third-parties found, 4.8 s blocking',
      makeTableDetails([
        { url: 'DoubleClick/Google Ads',             blockingTime: 1200 },
        { url: 'Krux Digital (Salesforce DMP)',      blockingTime:  800 },
        { url: 'Comscore Tag',                       blockingTime:  600 },
        { url: 'Optimizely Web',                     blockingTime:  480 },
        { url: 'LiveRamp (Arbor)',                   blockingTime:  360 },
        { url: 'Facebook Pixel',                     blockingTime:  280 },
      ])
    ),
    'uses-optimized-images': makeAudit(
      'uses-optimized-images', 'Efficiently encode images',
      'Optimized images load faster and consume less cellular data.',
      0.3, null, '680 KiB potential savings',
      makeTableDetails([
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601120000-cnn-hero.jpg',  wastedBytes: 280000, totalBytes: 380000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601100000-cnn-card1.jpg', wastedBytes: 145000, totalBytes: 210000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601090000-cnn-card2.jpg', wastedBytes:  98000, totalBytes: 155000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601080000-cnn-card3.jpg', wastedBytes:  78000, totalBytes: 130000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601070000-cnn-ad-bg.jpg', wastedBytes:  62000, totalBytes: 110000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601060000-cnn-promo.jpg', wastedBytes:  17000, totalBytes:  45000 },
      ])
    ),
    'modern-image-formats': makeAudit(
      'modern-image-formats', 'Serve images in next-gen formats',
      'Image formats like WebP and AVIF often provide better compression.',
      0.0, null, '890 KiB potential savings',
      makeTableDetails([
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601120000-cnn-hero.jpg',  wastedBytes: 310000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601100000-cnn-card1.jpg', wastedBytes: 165000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601090000-cnn-card2.jpg', wastedBytes: 115000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601080000-cnn-card3.jpg', wastedBytes:  95000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601070000-cnn-ad-bg.jpg', wastedBytes:  75000 },
        { url: 'https://cdn.cnn.com/cnnnext/dam/assets/240601060000-cnn-promo.jpg', wastedBytes:  30000 },
      ])
    ),
    'uses-text-compression': makeAudit(
      'uses-text-compression', 'Enable text compression',
      'Text-based resources should be served with compression (gzip, deflate or brotli).',
      0.0, null, '340 KiB potential savings',
      makeTableDetails([
        { url: 'https://www.cnn.com/2024/06/01/politics/article.html', wastedBytes: 180000, totalBytes: 310000 },
        { url: 'https://www.cnn.com/data/breaking-news.json',           wastedBytes:  98000, totalBytes: 140000 },
        { url: 'https://cdn.cnn.com/cnn/2024/fonts/CNN-Bold.woff',      wastedBytes:  62000, totalBytes: 104000 },
      ])
    ),
    'dom-size': makeAudit(
      'dom-size', 'Avoid an excessive DOM size',
      'A large DOM will increase memory usage and produce costly style recalculations.',
      0.0, 4800, '4,800 elements',
      makeTableDetails([
        { statistic: 'Total DOM Elements', value: 4800 },
        { statistic: 'Maximum DOM Depth',  value: 28   },
        { statistic: 'Maximum Children',   value: 126  },
      ])
    ),
    'server-response-time': makeAudit(
      'server-response-time', 'Reduce initial server response time',
      'Keep the server response time for the main document short.',
      0.0, 2100, 'Root document took 2,100 ms'
    ),
    'color-contrast': makeAudit(
      'color-contrast', 'Background and foreground colors have sufficient contrast',
      'Low-contrast text is difficult or impossible for many users to read.',
      0.0, null, null,
      makeTableDetails([
        { node: { nodeLabel: 'BREAKING' }, contrastRatio: 2.1, failureSummary: 'Expected contrast ratio of at least 4.5' },
        { node: { nodeLabel: 'WATCH LIVE' }, contrastRatio: 3.2, failureSummary: 'Expected contrast ratio of at least 4.5' },
      ])
    ),
    'image-alt': makeAudit(
      'image-alt', 'Image elements have [alt] attributes',
      'Informative elements should aim for short, descriptive alternate text.',
      0.0, null, null,
      makeTableDetails([
        { node: { nodeLabel: '<img class="media__image" src="...cnn-hero.jpg">' } },
        { node: { nodeLabel: '<img class="ad-slot__img" src="...ad-300x250.png">' } },
        { node: { nodeLabel: '<img class="logo-img" src="...cnn-logo.svg">' } },
      ])
    ),
    'label-content-name-mismatch': makeAudit(
      'label-content-name-mismatch', 'Interactive elements do not have accessible names that match their visible text',
      'Screen readers use accessible names to announce elements, so avoid overriding them.',
      0.0, null, null,
      makeTableDetails([
        { node: { nodeLabel: 'Watch CNN TV' } },
      ])
    ),
  };

  return makeLHR('https://www.cnn.com/', 'mobile', categories, audits);
}

// ---------------------------------------------------------------------------
// 4. IMAGE-HEAVY: National Geographic (rich photography, heavy images)
//    Typical: mobile perf ~18–30
// ---------------------------------------------------------------------------

function buildNatGeoMobile() {
  const categories = {
    performance: makeCategory(0.25, [
      { id: 'largest-contentful-paint',  weight: 10 },
      { id: 'cumulative-layout-shift',   weight: 15 },
      { id: 'total-blocking-time',       weight: 30 },
      { id: 'uses-optimized-images',     weight: 0  },
      { id: 'modern-image-formats',      weight: 0  },
      { id: 'offscreen-images',          weight: 0  },
      { id: 'uses-responsive-images',    weight: 0  },
      { id: 'render-blocking-resources', weight: 0  },
    ]),
    accessibility:    makeCategory(0.79, []),
    seo:              makeCategory(0.87, []),
    'best-practices': makeCategory(0.78, []),
  };

  const audits = {
    'largest-contentful-paint': makeAudit(
      'largest-contentful-paint', 'Largest Contentful Paint',
      'LCP marks the time at which the largest text or image is rendered.',
      0.0, 12600, '12.6 s'
    ),
    'cumulative-layout-shift': makeAudit(
      'cumulative-layout-shift', 'Cumulative Layout Shift',
      'CLS measures visual instability.',
      0.0, 0.32, '0.32'
    ),
    'total-blocking-time': makeAudit(
      'total-blocking-time', 'Total Blocking Time',
      'TBT.',
      0.15, 1800, '1,800 ms'
    ),
    'uses-optimized-images': makeAudit(
      'uses-optimized-images', 'Efficiently encode images',
      'Optimized images load faster and consume less cellular data.',
      0.0, null, '2.4 MiB potential savings',
      makeTableDetails([
        { url: 'https://i.natgeofe.com/n/hero-gallery-main-2024.jpg',     wastedBytes: 780000, totalBytes: 1200000 },
        { url: 'https://i.natgeofe.com/n/wildlife-photo-of-year.jpg',     wastedBytes: 520000, totalBytes:  900000 },
        { url: 'https://i.natgeofe.com/n/nature-explorer-banner.jpg',     wastedBytes: 380000, totalBytes:  640000 },
        { url: 'https://i.natgeofe.com/n/expedition-2024-cover.jpg',      wastedBytes: 290000, totalBytes:  480000 },
        { url: 'https://i.natgeofe.com/n/magazine-may-june-2024.jpg',     wastedBytes: 195000, totalBytes:  310000 },
        { url: 'https://i.natgeofe.com/n/animals-cats-documentary.jpg',   wastedBytes: 145000, totalBytes:  245000 },
      ])
    ),
    'modern-image-formats': makeAudit(
      'modern-image-formats', 'Serve images in next-gen formats',
      'Image formats like WebP and AVIF often provide better compression.',
      0.0, null, '2.1 MiB potential savings',
      makeTableDetails([
        { url: 'https://i.natgeofe.com/n/hero-gallery-main-2024.jpg',  wastedBytes: 680000 },
        { url: 'https://i.natgeofe.com/n/wildlife-photo-of-year.jpg',  wastedBytes: 450000 },
        { url: 'https://i.natgeofe.com/n/nature-explorer-banner.jpg',  wastedBytes: 330000 },
        { url: 'https://i.natgeofe.com/n/expedition-2024-cover.jpg',   wastedBytes: 250000 },
        { url: 'https://i.natgeofe.com/n/magazine-may-june-2024.jpg',  wastedBytes: 165000 },
        { url: 'https://i.natgeofe.com/n/animals-cats-documentary.jpg',wastedBytes: 120000 },
      ])
    ),
    'offscreen-images': makeAudit(
      'offscreen-images', 'Defer offscreen images',
      'Consider lazy-loading offscreen and hidden images to improve page load time.',
      0.0, null, '890 KiB potential savings',
      makeTableDetails([
        { url: 'https://i.natgeofe.com/n/nature-explorer-banner.jpg', wastedBytes: 260000 },
        { url: 'https://i.natgeofe.com/n/expedition-2024-cover.jpg',  wastedBytes: 190000 },
        { url: 'https://i.natgeofe.com/n/magazine-may-june-2024.jpg', wastedBytes: 155000 },
        { url: 'https://i.natgeofe.com/n/animals-cats-documentary.jpg',wastedBytes: 125000 },
        { url: 'https://i.natgeofe.com/n/ocean-planet-2024.jpg',      wastedBytes:  98000 },
        { url: 'https://i.natgeofe.com/n/stars-milky-way.jpg',        wastedBytes:  62000 },
      ])
    ),
    'uses-responsive-images': makeAudit(
      'uses-responsive-images', 'Properly size images',
      'Serve images that are appropriately-sized.',
      0.0, null, '1.1 MiB potential savings',
      makeTableDetails([
        { url: 'https://i.natgeofe.com/n/hero-gallery-main-2024.jpg',  wastedBytes: 520000, totalBytes: 1200000 },
        { url: 'https://i.natgeofe.com/n/wildlife-photo-of-year.jpg',  wastedBytes: 380000, totalBytes:  900000 },
        { url: 'https://i.natgeofe.com/n/nature-explorer-banner.jpg',  wastedBytes: 200000, totalBytes:  640000 },
      ])
    ),
    'render-blocking-resources': makeAudit(
      'render-blocking-resources', 'Eliminate render-blocking resources',
      'Resources are blocking the first paint of your page.',
      0.3, 1200, 'Potential savings of 1.2 s',
      makeTableDetails([
        { url: 'https://www.nationalgeographic.com/universal/en_US/ngtv/css/application.css', wastedMs: 920, totalBytes: 92000 },
        { url: 'https://www.nationalgeographic.com/universal/en_US/ngtv/css/fonts.css',       wastedMs: 280, totalBytes: 18000 },
      ])
    ),
  };

  return makeLHR('https://www.nationalgeographic.com/', 'mobile', categories, audits);
}

// ---------------------------------------------------------------------------
// 5. JS-HEAVY: Airbnb (React SPA, heavy JS, lots of code splitting)
//    Typical: mobile perf ~25–35
// ---------------------------------------------------------------------------

function buildAirbnbMobile() {
  const categories = {
    performance: makeCategory(0.30, [
      { id: 'largest-contentful-paint',          weight: 10 },
      { id: 'cumulative-layout-shift',           weight: 15 },
      { id: 'total-blocking-time',               weight: 30 },
      { id: 'unused-javascript',                 weight: 0  },
      { id: 'render-blocking-resources',         weight: 0  },
      { id: 'third-party-summary',               weight: 0  },
      { id: 'bootup-time',                       weight: 0  },
      { id: 'mainthread-work-breakdown',         weight: 0  },
      { id: 'uses-text-compression',             weight: 0  },
    ]),
    accessibility:    makeCategory(0.88, []),
    seo:              makeCategory(0.92, []),
    'best-practices': makeCategory(0.92, []),
  };

  const audits = {
    'largest-contentful-paint': makeAudit(
      'largest-contentful-paint', 'Largest Contentful Paint',
      'LCP marks the time at which the largest text or image is rendered.',
      0.0, 7800, '7.8 s'
    ),
    'cumulative-layout-shift': makeAudit(
      'cumulative-layout-shift', 'Cumulative Layout Shift',
      'CLS measures visual instability.',
      0.65, 0.12, '0.12'
    ),
    'total-blocking-time': makeAudit(
      'total-blocking-time', 'Total Blocking Time',
      'TBT.',
      0.0, 2850, '2,850 ms'
    ),
    'unused-javascript': makeAudit(
      'unused-javascript', 'Remove unused JavaScript',
      'Reduce unused JavaScript.',
      0.0, 4100, '4.1 s potential savings',
      makeTableDetails([
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/pdp-pages/app-XXXXXX.js',       wastedBytes: 245000, wastedMs: 1500 },
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/shared/vendor-react-XXXX.js',  wastedBytes: 198000, wastedMs: 1200 },
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/maps/google-maps-XXXX.js',      wastedBytes: 155000, wastedMs:  950 },
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/experiments-XXXX.js',           wastedBytes: 112000, wastedMs:  680 },
        { url: 'https://cdn.segment.com/analytics.js/v1/XXXXX/analytics.min.js',                                  wastedBytes:  78000, wastedMs:  480 },
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/polyfills-XXXX.js',             wastedBytes:  42000, wastedMs:  260 },
      ])
    ),
    'render-blocking-resources': makeAudit(
      'render-blocking-resources', 'Eliminate render-blocking resources',
      'Resources are blocking the first paint of your page.',
      0.0, 2200, 'Potential savings of 2.2 s',
      makeTableDetails([
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/critical-XXXX.css', wastedMs: 1400, totalBytes: 62000 },
        { url: 'https://fonts.googleapis.com/css2?family=Cereal&display=swap',                         wastedMs:  800, totalBytes: 24000 },
      ])
    ),
    'third-party-summary': makeAudit(
      'third-party-summary', 'Reduce the impact of third-party code',
      'Third-party code can significantly impact load performance.',
      0.0, null, '18 third-parties, 2.1 s blocking',
      makeTableDetails([
        { url: 'Google Analytics / Tag Manager',  blockingTime: 680 },
        { url: 'Segment Analytics',               blockingTime: 480 },
        { url: 'Braze (push notifications)',      blockingTime: 380 },
        { url: 'OneTrust (cookie consent)',        blockingTime: 280 },
        { url: 'Trustpilot widget',               blockingTime: 200 },
        { url: 'Intercom chat',                   blockingTime: 140 },
      ])
    ),
    'bootup-time': makeAudit(
      'bootup-time', 'Reduce JavaScript execution time',
      'Consider reducing the time spent parsing, compiling, and executing JS.',
      0.0, 7200, '7.2 s',
      makeTableDetails([
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/pdp-pages/app-XXXXXX.js',      scripting: 2800, total: 3200 },
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/shared/vendor-react-XXXX.js', scripting: 1900, total: 2200 },
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/maps/google-maps-XXXX.js',     scripting: 1100, total: 1300 },
        { url: 'https://cdn.segment.com/analytics.js/v1/XXXXX/analytics.min.js',                                 scripting:  650, total:  780 },
      ])
    ),
    'mainthread-work-breakdown': makeAudit(
      'mainthread-work-breakdown', 'Minimize main-thread work',
      'Consider reducing the time spent parsing, compiling and executing JS.',
      0.0, 9800, '9.8 s',
      makeTableDetails([
        { groupLabel: 'Script Evaluation',  duration: 5200 },
        { groupLabel: 'Style & Layout',     duration: 2100 },
        { groupLabel: 'Rendering',          duration: 1200 },
        { groupLabel: 'Garbage Collection', duration:  800 },
        { groupLabel: 'Parse HTML & CSS',   duration:  500 },
      ])
    ),
    'uses-text-compression': makeAudit(
      'uses-text-compression', 'Enable text compression',
      'Text-based resources should be served with compression.',
      0.5, null, '85 KiB potential savings',
      makeTableDetails([
        { url: 'https://a0.muscache.com/airbnb/static/packages/web/common/frontend/i18n/en-XXXX.json', wastedBytes: 55000, totalBytes: 88000 },
        { url: 'https://api.airbnb.com/v2/search_results?key=...',                                      wastedBytes: 30000, totalBytes: 48000 },
      ])
    ),
  };

  return makeLHR('https://www.airbnb.com/', 'mobile', categories, audits);
}

// ---------------------------------------------------------------------------
// Main: generate and save all fixtures
// ---------------------------------------------------------------------------

const fixtures = [
  { filename: 'gov-uk-mobile.json',          data: buildGovUkMobile()   },
  { filename: 'gov-uk-desktop.json',         data: buildGovUkDesktop()  },
  { filename: 'wikipedia-mobile.json',       data: buildWikipediaMobile() },
  { filename: 'cnn-mobile.json',             data: buildCnnMobile()     },
  { filename: 'national-geographic-mobile.json', data: buildNatGeoMobile() },
  { filename: 'airbnb-mobile.json',          data: buildAirbnbMobile()  },
];

const manifest = {
  'gov-uk': {
    url: 'https://www.gov.uk/', tag: 'high-performing',
    strategies: ['mobile', 'desktop'],
    results: [
      { strategy: 'mobile',  success: true, filename: 'gov-uk-mobile.json' },
      { strategy: 'desktop', success: true, filename: 'gov-uk-desktop.json' },
    ],
  },
  'wikipedia': {
    url: 'https://www.wikipedia.org/', tag: 'average',
    strategies: ['mobile'],
    results: [{ strategy: 'mobile', success: true, filename: 'wikipedia-mobile.json' }],
  },
  'cnn': {
    url: 'https://www.cnn.com/', tag: 'poor-performing',
    strategies: ['mobile'],
    results: [{ strategy: 'mobile', success: true, filename: 'cnn-mobile.json' }],
  },
  'national-geographic': {
    url: 'https://www.nationalgeographic.com/', tag: 'image-heavy',
    strategies: ['mobile'],
    results: [{ strategy: 'mobile', success: true, filename: 'national-geographic-mobile.json' }],
  },
  'airbnb': {
    url: 'https://www.airbnb.com/', tag: 'js-heavy',
    strategies: ['mobile'],
    results: [{ strategy: 'mobile', success: true, filename: 'airbnb-mobile.json' }],
  },
};

/** Minimum byte size that indicates a real (API-fetched) PSI fixture. */
const REAL_FIXTURE_THRESHOLD_BYTES = 50 * 1024; // 50 KB

/**
 * Write a synthetic fixture unless a real one already exists.
 * A file is considered "real" if it exists and is larger than the threshold.
 *
 * @param {string} filename
 * @param {object} data
 */
function writeSyntheticFixture(filename, data) {
  const path = join(__dir, filename);

  if (existsSync(path)) {
    const { size } = statSync(path);
    if (size > REAL_FIXTURE_THRESHOLD_BYTES) {
      console.log(`  Skipping ${filename} — existing real fixture detected (${Math.round(size / 1024)} KB).`);
      return;
    }
  }

  console.log(`  Writing synthetic ${filename}.`);
  writeFileSync(path, JSON.stringify(data, null, 2), 'utf8');
}

fixtures.forEach(({ filename, data }) => writeSyntheticFixture(filename, data));

// manifest.json is always overwritten — it's metadata, never a real PSI payload
writeFileSync(join(__dir, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf8');
console.log('  Writing synthetic manifest.json.');
console.log('\nDone.');

