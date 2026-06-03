// tests/parser.test.js
import { test } from 'node:test';
import assert from 'node:assert';
import { parseMarkdownReport } from '../utils/report-parser.js';

// Sample markdown resembling the output from report-builder.js
const sampleMarkdown = `# PageSpeed Optimization Report

**URL**: https://example.com  
**Generated**: 2026-06-02  
**Strategies**: mobile + desktop  
**Tool**: Lighthouse Handoff

## 1. Executive Summary

| Category            | Mobile | Desktop |
| ------------------- | ------ | ------- |
| Performance         | 45     | 82      |
| Accessibility       | 88     | 90      |

## 2. Core Web Vitals
- **LCP (Largest Contentful Paint)**: Mobile 4.2s 🔴 | Desktop 1.8s 🟢
- **CLS (Cumulative Layout Shift)**: Mobile 0.15 🟡 | Desktop 0.02 🟢

## 3. Highest-Impact Issues (Ranked)
> This section is designed to be self-contained.
**Top Priorities to Address First:**
1. Reduce unused JavaScript
2. Eliminate render-blocking resources

### 1. Reduce unused JavaScript
**Impact**: High (95) | **Category**: performance | **Affected**: mobile + desktop
**Measured**: 1.5s saved

**Concrete items from the audit (top offenders):**
1. https://example.com/main.js — wasted ~450 KiB

**Problem**: The page loads JavaScript that is not needed during initial render.

**Recommended Fix**:
- Split heavy scripts by page.
- Defer non-critical scripts.

**Coding Agent Instructions**:
Inspect all scripts loaded on this page and defer them.

### 2. Eliminate render-blocking resources
**Impact**: Critical (130) | **Category**: performance | **Affected**: mobile

**Problem**: Resources are blocking the first paint of your page.

**Recommended Fix**:
- Defer non-critical CSS.

**Coding Agent Instructions**:
Inline critical CSS.

## 4. Easy Wins (Quick Improvements)
These are typically low-risk and fast to implement.
- Use font-display: swap for web fonts.
- Add alt attributes to images.

## 5. Medium Fixes
- Optimize images.
- Code split javascript.

## 6. Hard / Architectural Fixes
- Redesign build chain.

## 9. Guardrails — Do NOT Do These Things
**Strict rules for any coding agent:**
- Do not redesign layouts.
- Do not remove tracking scripts.

## 10. Acceptance Criteria & Retest Instructions
**After making changes:**
1. Re-run this extension.
2. The score should improve.
3. No console errors.
`;

test('parseMarkdownReport parses metadata correctly', () => {
  const result = parseMarkdownReport(sampleMarkdown);
  assert.strictEqual(result.url, 'https://example.com');
  assert.strictEqual(result.date, '2026-06-02');
  assert.strictEqual(result.strategies, 'mobile + desktop');
});

test('parseMarkdownReport parses issues correctly', () => {
  const result = parseMarkdownReport(sampleMarkdown);
  assert.strictEqual(result.issues.length, 2);

  const issue1 = result.issues[0];
  assert.strictEqual(issue1.rank, 1);
  assert.strictEqual(issue1.title, 'Reduce unused JavaScript');
  assert.strictEqual(issue1.impact, 'High');
  assert.strictEqual(issue1.category, 'performance');
  assert.strictEqual(issue1.affected, 'mobile + desktop');
  assert.strictEqual(issue1.measured, '1.5s saved');
  assert.match(issue1.problem, /JavaScript that is not needed/);
  assert.match(issue1.recommendedFix, /Split heavy scripts/);
  assert.match(issue1.agentInstructions, /Inspect all scripts/);

  const issue2 = result.issues[1];
  assert.strictEqual(issue2.rank, 2);
  assert.strictEqual(issue2.title, 'Eliminate render-blocking resources');
  assert.strictEqual(issue2.impact, 'Critical');
  assert.strictEqual(issue2.category, 'performance');
  assert.strictEqual(issue2.affected, 'mobile');
  assert.strictEqual(issue2.measured, '');
  assert.match(issue2.problem, /Resources are blocking/);
  assert.match(issue2.recommendedFix, /Defer non-critical CSS/);
  assert.match(issue2.agentInstructions, /Inline critical CSS/);
});

test('parseMarkdownReport parses concrete fixes (easy/medium/hard) correctly', () => {
  const result = parseMarkdownReport(sampleMarkdown);
  assert.strictEqual(result.concreteFixes.length, 5);
  assert.strictEqual(result.concreteFixes[0], 'Use font-display: swap for web fonts.');
  assert.strictEqual(result.concreteFixes[2], 'Optimize images.');
  assert.strictEqual(result.concreteFixes[4], 'Redesign build chain.');
});

test('parseMarkdownReport parses guardrails correctly', () => {
  const result = parseMarkdownReport(sampleMarkdown);
  assert.strictEqual(result.guardrails.length, 2);
  assert.strictEqual(result.guardrails[0], 'Do not redesign layouts.');
  assert.strictEqual(result.guardrails[1], 'Do not remove tracking scripts.');
});

test('parseMarkdownReport parses acceptance criteria correctly', () => {
  const result = parseMarkdownReport(sampleMarkdown);
  assert.strictEqual(result.acceptanceCriteria.length, 3);
  assert.strictEqual(result.acceptanceCriteria[0], 'Re-run this extension.');
  assert.strictEqual(result.acceptanceCriteria[1], 'The score should improve.');
});
