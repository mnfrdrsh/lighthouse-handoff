# Output Quality Review

## What looks good
1. **Clear Mode Differentiation**: The output modes successfully cater to different audiences. The `cursor` and `github` modes give actionable developer instructions, while the `client` mode successfully removes jargon and highlights business impact.
2. **Prioritization**: Issues are correctly sorted by impact, and separating Quick Wins from Priority Fixes gives clear, progressive steps.
3. **Validation Criteria**: The Acceptance Criteria block is well-structured and provides concrete benchmarks like "LCP must be below 2.5s".

## What needed improvement
1. **Vague Instructions**: Instructions for complex issues like "unused-javascript" were too generic ("Open DevTools"). Developers and AI agents need specific filenames.
2. **Missing Identifiers**: The `cursor` and `github` modes lacked the exact `audit-id`, making it difficult for an agent to trace back to the raw Lighthouse data if necessary.
3. **Overzealous Simplification**: The `client` mode was mistakenly translating file extensions (e.g., `main.css` to `main.visual styling code`).

## Changes made
1. **Propagated Audit Details**: Updated `parser.js`, `normalizer.js`, and `scoring.js` to extract and propagate `details.items` up to the `RankedIssue` objects.
2. **Enhanced Mock Provider Instructions**: Updated `mock.js` to parse these `items` and specifically call out offending scripts, CSS files, images, and HTML elements by name before giving general advice.
3. **Added Audit IDs**: Modified `cursor.js` and `github.js` to append the Lighthouse `audit-id` directly to the issue heading.
4. **Fixed Client Parsing**: Changed the jargon-replacement regex in `client.js` to use negative lookbehinds (e.g. `/(?<!\.)\bCSS\b/gi`), preventing file extensions from being translated.

## Remaining limitations
1. **Static Advice**: While much better due to file name injection, the core advice is still static text from `mock.js`. It isn't deeply analyzing the code architecture (e.g., "Replace lodash with lodash-es in your webpack config").
2. **Context-Free URLs**: Some URLs are still long and messy if they don't have a clean pathname to extract.
3. **Third-Party Identification**: Identifying third-parties relies on Lighthouse's built-in grouping; custom scripts or new trackers may just appear as generic domain names.

*These remaining limitations are expected at this stage and will be resolved naturally when replacing the MockProvider with the Liquid/Claude AI implementations.*
