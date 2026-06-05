// tests/ai-engine.test.js
// Tests for Tasks 1–5: normalizer, scoring, mock provider, AI analysis schema.
// Run with: node --test tests/ai-engine.test.js

import { test, describe } from 'node:test';
import assert from 'node:assert/strict';

// ---------------------------------------------------------------------------
// Minimal PSI fixture (stripped-down lighthouseResult)
// ---------------------------------------------------------------------------

const MOBILE_LHR = {
  finalUrl: 'https://example.com',
  requestedUrl: 'https://example.com',
  categories: {
    performance:     { score: 0.42, auditRefs: [
      { id: 'unused-javascript',  weight: 3 },
      { id: 'render-blocking-resources', weight: 3 },
      { id: 'largest-contentful-paint',  weight: 10 },
      { id: 'cumulative-layout-shift',   weight: 15 },
    ]},
    accessibility:   { score: 0.88, auditRefs: [] },
    seo:             { score: 0.91, auditRefs: [] },
    'best-practices':{ score: 0.75, auditRefs: [] },
  },
  audits: {
    'unused-javascript': {
      id: 'unused-javascript',
      title: 'Remove unused JavaScript',
      description: 'Reduce unused JavaScript.',
      score: 0.1,
      numericValue: 2300,
      displayValue: '2.3s',
      details: { items: [{ url: 'https://example.com/main.js', wastedBytes: 120000 }] },
    },
    'render-blocking-resources': {
      id: 'render-blocking-resources',
      title: 'Eliminate render-blocking resources',
      description: 'Resources are blocking the first paint.',
      score: 0.3,
      numericValue: 900,
      displayValue: '0.9s',
      details: { items: [{ url: 'https://example.com/style.css', wastedMs: 900 }] },
    },
    'largest-contentful-paint': {
      id: 'largest-contentful-paint',
      title: 'Largest Contentful Paint',
      description: 'LCP marks the time at which the largest text or image element is rendered.',
      score: 0.0,
      numericValue: 5200,  // 5.2s → Critical threshold
      displayValue: '5.2s',
    },
    'cumulative-layout-shift': {
      id: 'cumulative-layout-shift',
      title: 'Cumulative Layout Shift',
      description: 'CLS measures visual instability.',
      score: 0.1,
      numericValue: 0.3,   // >0.25 → Critical threshold
      displayValue: '0.30',
    },
    'total-blocking-time': {
      id: 'total-blocking-time',
      title: 'Total Blocking Time',
      description: 'TBT.',
      score: 0.3,
      numericValue: 750,   // >600 → High threshold
      displayValue: '750ms',
    },
    'interaction-to-next-paint': {
      id: 'interaction-to-next-paint',
      title: 'Interaction to Next Paint',
      score: null, // informational — should be excluded
      numericValue: 100,
    },
  },
};

const PSI_RESULTS = [
  { strategy: 'mobile', success: true, data: { lighthouseResult: MOBILE_LHR } },
];

// ---------------------------------------------------------------------------
// Import modules under test
// ---------------------------------------------------------------------------

import { normalizeLighthouseResult, normalizeResults } from '../src/lighthouse/normalizer.js';
import { buildRankedIssueList, rankIssues }            from '../src/lighthouse/scoring.js';
import { parseAndRank }                                from '../src/lighthouse/parser.js';
import { MockProvider }                                from '../src/ai/providers/mock.js';

// ---------------------------------------------------------------------------
// Task 1: Normalizer tests
// ---------------------------------------------------------------------------

describe('Normalizer', () => {
  test('normalizeLighthouseResult produces correct shape', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');

    assert.equal(summary.url,      'https://example.com');
    assert.equal(summary.strategy, 'mobile');
    assert.equal(typeof summary.scores.performance,   'number');
    assert.equal(typeof summary.scores.accessibility, 'number');
    assert.equal(typeof summary.scores.seo,           'number');
    assert.equal(typeof summary.scores.bestPractices, 'number');
    assert.equal(typeof summary.metrics.lcp,  'number');
    assert.equal(typeof summary.metrics.cls,  'number');
    assert.equal(typeof summary.metrics.inp,  'number');
    assert.equal(typeof summary.metrics.tbt,  'number');
    assert.ok(Array.isArray(summary.opportunities));
  });

  test('scores are converted from 0–1 to 0–100 integers', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    assert.equal(summary.scores.performance,   42);
    assert.equal(summary.scores.accessibility, 88);
    assert.equal(summary.scores.seo,           91);
    assert.equal(summary.scores.bestPractices, 75);
  });

  test('metrics are in raw numeric units', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    assert.equal(summary.metrics.lcp, 5200);
    assert.equal(summary.metrics.tbt, 750);
    assert.ok(summary.metrics.cls > 0.29 && summary.metrics.cls < 0.31);
  });

  test('informational audits (score=null) are excluded from opportunities', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const ids = summary.opportunities.map(o => o.id);
    assert.ok(!ids.includes('interaction-to-next-paint'), 'null-score audit should be excluded');
  });

  test('passed audits (score=1) are excluded', () => {
    const lhr = {
      ...MOBILE_LHR,
      audits: {
        ...MOBILE_LHR.audits,
        'passed-audit': {
          id: 'passed-audit',
          title: 'Passed',
          score: 1,
        },
      },
      categories: {
        ...MOBILE_LHR.categories,
        performance: {
          ...MOBILE_LHR.categories.performance,
          auditRefs: [
            ...MOBILE_LHR.categories.performance.auditRefs,
            { id: 'passed-audit', weight: 1 },
          ],
        },
      },
    };
    const summary = normalizeLighthouseResult(lhr, 'mobile');
    assert.ok(!summary.opportunities.some(o => o.id === 'passed-audit'));
  });

  test('normalizeResults filters failed strategies', () => {
    const results = [
      { strategy: 'mobile', success: true,  data: { lighthouseResult: MOBILE_LHR } },
      { strategy: 'desktop', success: false, data: null },
    ];
    const summaries = normalizeResults(results);
    assert.equal(summaries.length, 1);
    assert.equal(summaries[0].strategy, 'mobile');
  });
});

// ---------------------------------------------------------------------------
// Task 2: Scoring / Priority Engine tests
// ---------------------------------------------------------------------------

describe('Priority Engine', () => {
  test('buildRankedIssueList returns array of RankedIssue', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const ranked  = buildRankedIssueList(summary);

    assert.ok(Array.isArray(ranked));
    assert.ok(ranked.length > 0);

    for (const issue of ranked) {
      assert.ok(['critical', 'high', 'medium', 'low'].includes(issue.priority), `unexpected priority: ${issue.priority}`);
      assert.equal(typeof issue.title,       'string');
      assert.equal(typeof issue.impactScore, 'number');
    }
  });

  test('LCP > 4s is ranked critical', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const ranked  = buildRankedIssueList(summary);
    const lcpIssue = ranked.find(i => i.id === 'largest-contentful-paint' || i.id === '__lcp_critical');
    assert.ok(lcpIssue, 'LCP issue should appear in ranked list');
    assert.equal(lcpIssue.priority, 'critical');
  });

  test('CLS > 0.25 is ranked critical', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const ranked  = buildRankedIssueList(summary);
    const clsIssue = ranked.find(i => i.id === 'cumulative-layout-shift' || i.id === '__cls_critical');
    assert.ok(clsIssue, 'CLS issue should appear in ranked list');
    assert.equal(clsIssue.priority, 'critical');
  });

  test('unused-javascript is ranked high', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const ranked  = buildRankedIssueList(summary);
    const jsIssue = ranked.find(i => i.id.includes('unused-javascript'));
    assert.ok(jsIssue, 'unused-javascript should appear in ranked list');
    assert.ok(['high', 'critical'].includes(jsIssue.priority), `expected high/critical, got ${jsIssue.priority}`);
  });

  test('list is sorted by impactScore descending', () => {
    const summary = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const ranked  = buildRankedIssueList(summary);
    for (let i = 0; i < ranked.length - 1; i++) {
      assert.ok(
        ranked[i].impactScore >= ranked[i + 1].impactScore,
        `rank[${i}] (${ranked[i].impactScore}) should be >= rank[${i+1}] (${ranked[i+1].impactScore})`
      );
    }
  });
});

// ---------------------------------------------------------------------------
// Task 3 & 4: Mock Provider + AIAnalysis schema tests
// ---------------------------------------------------------------------------

describe('MockProvider', () => {
  test('analyze returns a valid AIAnalysis', async () => {
    const summary  = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const provider = new MockProvider();
    const analysis = await provider.analyze(summary);

    assert.equal(provider.name, 'mock');
    assert.equal(typeof analysis.executiveSummary, 'string');
    assert.ok(analysis.executiveSummary.length > 0);

    assert.ok(Array.isArray(analysis.quickWins));
    assert.ok(analysis.quickWins.length > 0);
    assert.ok(analysis.quickWins.every(w => typeof w === 'string'));

    assert.ok(Array.isArray(analysis.priorityFixes));
    assert.ok(analysis.priorityFixes.length > 0);
    for (const fix of analysis.priorityFixes) {
      assert.equal(typeof fix.title,       'string');
      assert.equal(typeof fix.reasoning,   'string');
      assert.ok(Array.isArray(fix.instructions));
      assert.ok(fix.instructions.length > 0);
      assert.ok(fix.instructions.every(i => typeof i === 'string'));
    }

    assert.ok(Array.isArray(analysis.acceptanceCriteria));
    assert.ok(analysis.acceptanceCriteria.length > 0);
    assert.equal(analysis.modelId, 'mock');
  });

  test('executiveSummary reflects actual scores', async () => {
    const summary  = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const provider = new MockProvider();
    const analysis = await provider.analyze(summary);

    assert.match(analysis.executiveSummary, /42/,   'should include performance score');
    assert.match(analysis.executiveSummary, /MOBILE/i, 'should include strategy');
  });

  test('acceptanceCriteria includes LCP and CLS thresholds', async () => {
    const summary  = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const provider = new MockProvider();
    const analysis = await provider.analyze(summary);

    const all = analysis.acceptanceCriteria.join(' ');
    assert.match(all, /LCP/i);
    assert.match(all, /CLS/i);
  });

  test('priorityFixes are deterministic (same input → same output)', async () => {
    const summary  = normalizeLighthouseResult(MOBILE_LHR, 'mobile');
    const provider = new MockProvider();

    const a1 = await provider.analyze(summary);
    const a2 = await provider.analyze(summary);

    assert.deepEqual(a1.quickWins,      a2.quickWins);
    assert.deepEqual(a1.priorityFixes,  a2.priorityFixes);
    assert.deepEqual(a1.acceptanceCriteria, a2.acceptanceCriteria);
  });
});

// ---------------------------------------------------------------------------
// Task 1+2 Integration: parseAndRank
// ---------------------------------------------------------------------------

describe('parseAndRank integration', () => {
  test('returns summaries and ranked issues', () => {
    const { summaries, rankedIssues } = parseAndRank(PSI_RESULTS);

    assert.equal(summaries.length, 1);
    assert.ok(rankedIssues.length > 0);
  });

  test('failed strategies are excluded from summaries', () => {
    const results = [
      ...PSI_RESULTS,
      { strategy: 'desktop', success: false, data: null },
    ];
    const { summaries } = parseAndRank(results);
    assert.equal(summaries.length, 1);
  });

  test('multi-strategy results merge issues by id', () => {
    const desktopLhr = JSON.parse(JSON.stringify(MOBILE_LHR));
    desktopLhr.finalUrl = 'https://example.com';
    // Change a score to ensure the worse one is kept
    desktopLhr.audits['unused-javascript'].score = 0.5;
    desktopLhr.audits['unused-javascript'].numericValue = 1000;

    const multiResults = [
      { strategy: 'mobile',  success: true, data: { lighthouseResult: MOBILE_LHR } },
      { strategy: 'desktop', success: true, data: { lighthouseResult: desktopLhr  } },
    ];

    const { rankedIssues } = parseAndRank(multiResults);

    // unused-javascript should appear once, with mobile strategy as primary
    const jsIssues = rankedIssues.filter(i => i.id === 'unused-javascript');
    assert.equal(jsIssues.length, 1, 'duplicate audit IDs should be merged');

    // affectedStrategies should include both
    const js = jsIssues[0];
    assert.ok(js.affectedStrategies?.includes('mobile'),  'should include mobile');
    assert.ok(js.affectedStrategies?.includes('desktop'), 'should include desktop');
  });
});
