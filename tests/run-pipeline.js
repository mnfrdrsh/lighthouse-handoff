// tests/run-pipeline.js
// Runs the AI Engine pipeline on saved PSI fixtures and generates output snapshots.
// Also evaluates output quality programmatically.
// Run with: node tests/run-pipeline.js

import { readFileSync, writeFileSync, readdirSync, mkdirSync } from 'node:fs';
import { join, dirname }  from 'node:path';
import { fileURLToPath }  from 'node:url';

const __dir = dirname(fileURLToPath(import.meta.url));

const FIXTURE_DIR  = join(__dir, 'fixtures', 'sample-psi');
const SNAPSHOT_DIR = join(__dir, 'output-snapshots');
mkdirSync(SNAPSHOT_DIR, { recursive: true });

import { normalizeLighthouseResult } from '../src/lighthouse/normalizer.js';
import { buildRankedIssueList }      from '../src/lighthouse/scoring.js';
import { MockProvider }              from '../src/ai/providers/mock.js';
import { generateMarkdown }          from '../src/output/markdown.js';

const OUTPUT_MODES = ['cursor', 'github', 'client'];

async function processFixture(label, strategy, psiData) {
  const lhr     = psiData.lighthouseResult;
  const summary = normalizeLighthouseResult(lhr, strategy);
  const ranked  = buildRankedIssueList(summary);
  const provider = new MockProvider();
  const analysis = await provider.analyze(summary);

  const outputs = {};
  for (const mode of OUTPUT_MODES) {
    outputs[mode] = generateMarkdown(analysis, summary, mode, 'mock');
  }

  return { summary, ranked, analysis, outputs };
}

function loadManifest() {
  const path = join(FIXTURE_DIR, 'manifest.json');
  return JSON.parse(readFileSync(path, 'utf8'));
}

function loadFixture(filename) {
  const path = join(FIXTURE_DIR, filename);
  return JSON.parse(readFileSync(path, 'utf8'));
}

// ---------------------------------------------------------------------------
// Quality checks
// ---------------------------------------------------------------------------

function evaluateOutput(label, strategy, mode, markdown, analysis, ranked) {
  const issues = [];
  const passes = [];

  // Check 1: Has priority fixes
  if (analysis.priorityFixes.length === 0) {
    issues.push('NO_PRIORITY_FIXES: analysis returned no priority fixes');
  } else {
    passes.push(`priority_fixes: ${analysis.priorityFixes.length} fix(es) generated`);
  }

  // Check 2: Instructions are specific (not generic)
  const vaguePatterns = [/optimize images/i, /improve performance/i, /fix issues/i];
  const allInstructions = analysis.priorityFixes.flatMap(f => f.instructions).join(' ');
  const vagueMatches = vaguePatterns.filter(p => p.test(allInstructions));
  if (vagueMatches.length > 0) {
    issues.push(`VAGUE_INSTRUCTIONS: matched patterns: ${vagueMatches.map(p => p.toString()).join(', ')}`);
  } else {
    passes.push('instructions: no vague "optimize images" pattern detected');
  }

  // Check 3: Has acceptance criteria
  if (analysis.acceptanceCriteria.length === 0) {
    issues.push('NO_ACCEPTANCE_CRITERIA');
  } else {
    passes.push(`acceptance_criteria: ${analysis.acceptanceCriteria.length} criteria`);
  }

  // Check 4: LCP mentioned in criteria when score is poor
  if (analysis.acceptanceCriteria.join(' ').match(/LCP/i)) {
    passes.push('lcp_criteria: LCP threshold present in acceptance criteria');
  } else {
    issues.push('MISSING_LCP_CRITERIA: LCP threshold not in acceptance criteria');
  }

  // Check 5: Markdown has expected sections by mode
  if (mode === 'cursor') {
    if (!markdown.includes('Priority Fixes')) issues.push('CURSOR: missing Priority Fixes section');
    if (!markdown.includes('Guardrails'))      issues.push('CURSOR: missing Guardrails section');
    if (!markdown.includes('Acceptance Criteria')) issues.push('CURSOR: missing Acceptance Criteria section');
    if (markdown.includes('Priority Fixes') && markdown.includes('Guardrails')) {
      passes.push('cursor_structure: has Priority Fixes + Guardrails');
    }
  }
  if (mode === 'github') {
    if (!markdown.includes('- [ ]')) issues.push('GITHUB: missing task list checkboxes');
    else passes.push('github_structure: has task-list checkboxes');
  }
  if (mode === 'client') {
    if (markdown.match(/JavaScript/i) && !markdown.match(/website code/i)) {
      issues.push('CLIENT: raw "JavaScript" term not simplified in client output');
    } else {
      passes.push('client_structure: technical terms simplified');
    }
    if (!markdown.includes('Business') && !markdown.includes('business impact') && !markdown.includes('matters')) {
      // soft warning
    }
  }

  // Check 6: Output modes feel different
  // (this is checked in the caller comparing across modes)

  // Check 7: No hallucinated certainty claims
  const certaintyPatterns = [/definitely fix/i, /guaranteed/i, /will definitely/i];
  const hasCertainty = certaintyPatterns.some(p => p.test(markdown));
  if (hasCertainty) {
    issues.push('OVERCLAIMS_CERTAINTY: found overconfident language');
  } else {
    passes.push('certainty: no overclaiming language detected');
  }

  return { issues, passes };
}

async function main() {
  console.log('=== Running AI Engine Pipeline on Fixtures ===\n');

  const manifest = loadManifest();
  const allResults = {};
  const qualityReport = [];

  for (const [label, entry] of Object.entries(manifest)) {
    console.log(`\n── ${label} (${entry.tag}) ──`);

    const labelResults = {};

    for (const { strategy, success, filename } of entry.results) {
      if (!success || !filename) {
        console.log(`  [${strategy}] skipped (fetch failed)`);
        continue;
      }

      try {
        const psiData = loadFixture(filename);
        const { summary, ranked, analysis, outputs } = await processFixture(label, strategy, psiData);

        console.log(`  [${strategy}] perf=${summary.scores.performance} a11y=${summary.scores.accessibility} seo=${summary.scores.seo}`);
        console.log(`    metrics: LCP=${(summary.metrics.lcp/1000).toFixed(2)}s CLS=${summary.metrics.cls.toFixed(3)} TBT=${Math.round(summary.metrics.tbt)}ms`);
        console.log(`    ranked issues: ${ranked.length} total (${ranked.filter(i=>i.priority==='critical').length} critical, ${ranked.filter(i=>i.priority==='high').length} high)`);
        console.log(`    analysis: ${analysis.priorityFixes.length} priority fixes, ${analysis.quickWins.length} quick wins`);

        // Save outputs
        for (const mode of OUTPUT_MODES) {
          const outFile = `${label}-${strategy}-${mode}.md`;
          const outPath = join(SNAPSHOT_DIR, outFile);
          writeFileSync(outPath, outputs[mode], 'utf8');
          console.log(`    → saved ${outFile}`);

          // Evaluate quality
          const qa = evaluateOutput(label, strategy, mode, outputs[mode], analysis, ranked);
          qualityReport.push({
            label, strategy, mode, outFile,
            scores: summary.scores,
            metrics: { lcp: summary.metrics.lcp, cls: summary.metrics.cls, tbt: summary.metrics.tbt },
            priorityFixCount: analysis.priorityFixes.length,
            quickWinCount:    analysis.quickWins.length,
            qa,
          });
        }

        // Save summary JSON
        const summaryOut = {
          url:     summary.url,
          strategy,
          scores:  summary.scores,
          metrics: summary.metrics,
          rankedIssues: ranked.slice(0, 10).map(i => ({
            id: i.id, title: i.title, priority: i.priority, impactScore: i.impactScore,
          })),
          analysisSnapshot: {
            executiveSummaryLength: analysis.executiveSummary.length,
            quickWinCount:          analysis.quickWins.length,
            priorityFixCount:       analysis.priorityFixes.length,
            priorityFixTitles:      analysis.priorityFixes.map(f => f.title),
            acceptanceCriteriaCount: analysis.acceptanceCriteria.length,
          },
        };
        writeFileSync(
          join(SNAPSHOT_DIR, `${label}-${strategy}-summary.json`),
          JSON.stringify(summaryOut, null, 2),
          'utf8'
        );

        labelResults[strategy] = { summary, ranked, analysis };

      } catch (err) {
        console.error(`  [${strategy}] ERROR: ${err.message}`);
      }
    }

    // Cross-mode differentiation check
    const firstLabel = entry.results.find(r => r.success && r.filename)?.strategy;
    if (firstLabel && labelResults[firstLabel]) {
      const { analysis, summary } = labelResults[firstLabel];
      const mdCursor = generateMarkdown(analysis, summary, 'cursor', 'mock');
      const mdGithub = generateMarkdown(analysis, summary, 'github', 'mock');
      const mdClient = generateMarkdown(analysis, summary, 'client', 'mock');

      const allSame = (mdCursor === mdGithub) || (mdGithub === mdClient) || (mdCursor === mdClient);
      if (allSame) {
        qualityReport.push({ label, crossModeIssue: 'OUTPUT_MODES_IDENTICAL: some modes produce identical output' });
      } else {
        qualityReport.push({ label, crossModeIssue: null, crossModePass: 'modes produce distinct output ✓' });
      }
    }

    allResults[label] = labelResults;
  }

  // Write quality report
  writeFileSync(
    join(SNAPSHOT_DIR, 'quality-report.json'),
    JSON.stringify(qualityReport, null, 2),
    'utf8'
  );
  console.log('\n✓ Saved quality-report.json');

  // Print summary
  console.log('\n=== Quality Check Summary ===\n');
  const allIssues = qualityReport.flatMap(r => (r.qa?.issues ?? []));
  const allPasses = qualityReport.flatMap(r => (r.qa?.passes ?? []));
  console.log(`Checks passed: ${allPasses.length}`);
  console.log(`Issues found:  ${allIssues.length}`);
  if (allIssues.length > 0) {
    console.log('\nIssues:');
    const unique = [...new Set(allIssues)];
    unique.forEach(i => console.log(`  ✗ ${i}`));
  }
}

main().catch(err => {
  console.error('Fatal:', err);
  process.exit(1);
});
