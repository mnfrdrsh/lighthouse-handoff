// utils/report-parser.js

/**
 * Parses the generated markdown report into a structured object for custom UI rendering.
 * @param {string} markdown
 * @returns {Object} Structured report data
 */
export function parseMarkdownReport(markdown) {
  if (!markdown) return null;

  // Extract Metadata
  const urlMatch = markdown.match(/\*\*URL\*\*:\s*([^\n\r]+)/i);
  const dateMatch = markdown.match(/\*\*Generated\*\*:\s*([^\n\r]+)/i);
  const strategiesMatch = markdown.match(/\*\*Strategies\*\*:\s*([^\n\r]+)/i);

  const url = urlMatch ? urlMatch[1].replace(/&nbsp;/g, ' ').replace(/\s+$/, '').trim() : '';
  const date = dateMatch ? dateMatch[1].replace(/&nbsp;/g, ' ').replace(/\s+$/, '').trim() : '';
  const strategies = strategiesMatch ? strategiesMatch[1].replace(/&nbsp;/g, ' ').replace(/\s+$/, '').trim() : '';

  // Extract issues from Section 3
  const issues = [];
  const issuesSectionMatch = markdown.match(/## 3\. Highest-Impact Issues[\s\S]*?(?=## 4\.|$)/i);
  if (issuesSectionMatch) {
    const issuesSection = issuesSectionMatch[0];
    const issueChunks = issuesSection.split(/\n###\s+/);
    // The first chunk is the intro, remaining are actual issues
    for (let i = 1; i < issueChunks.length; i++) {
      const chunk = issueChunks[i];
      const lines = chunk.split('\n');
      const titleLine = lines[0].trim();
      const titleMatch = titleLine.match(/^(\d+)\.\s+(.+)$/);
      
      const rank = titleMatch ? parseInt(titleMatch[1], 10) : i;
      const title = titleMatch ? titleMatch[2].trim() : titleLine;

      // Parse metadata: **Impact**: High (95) | **Category**: performance | **Affected**: mobile + desktop
      let impact = 'Medium';
      let category = 'performance';
      let affected = 'mobile';
      
      const metaLine = lines.find(l => l.includes('**Impact**:'));
      if (metaLine) {
        const impactMatch = metaLine.match(/\*\*Impact\*\*:\s*([^\s(|]+)/i);
        const catMatch = metaLine.match(/\*\*Category\*\*:\s*([^\s|]+)/i);
        const affectedMatch = metaLine.match(/\*\*Affected\*\*:\s*([^\n\r|]+)/i);
        
        if (impactMatch) impact = impactMatch[1].trim();
        if (catMatch) category = catMatch[1].trim();
        if (affectedMatch) affected = affectedMatch[1].trim();
      }

      // Parse measured value
      let measured = '';
      const measuredLine = lines.find(l => l.includes('**Measured**:'));
      if (measuredLine) {
        const measuredMatch = measuredLine.match(/\*\*Measured\*\*:\s*([^\n\r]+)/i);
        if (measuredMatch) measured = measuredMatch[1].trim();
      }

      // Extract problem text
      let problem = '';
      const problemStartIndex = lines.findIndex(l => l.startsWith('**Problem**:'));
      if (problemStartIndex !== -1) {
        const problemLines = [];
        for (let j = problemStartIndex; j < lines.length; j++) {
          const l = lines[j];
          if (j > problemStartIndex && (l.startsWith('**') || l.startsWith('###') || l.startsWith('##'))) {
            break;
          }
          problemLines.push(l);
        }
        problem = problemLines.join('\n').replace(/^\*\*Problem\*\*:\s*/i, '').trim();
      }

      // Extract recommended fix text
      let recommendedFix = '';
      const recStartIndex = lines.findIndex(l => l.startsWith('**Recommended Fix**:'));
      if (recStartIndex !== -1) {
        const recLines = [];
        for (let j = recStartIndex; j < lines.length; j++) {
          const l = lines[j];
          if (j > recStartIndex && (l.startsWith('**') || l.startsWith('###') || l.startsWith('##'))) {
            break;
          }
          recLines.push(l);
        }
        recommendedFix = recLines.join('\n').replace(/^\*\*Recommended Fix\*\*:\s*/i, '').trim();
      }

      // Extract coding agent instructions
      let agentInstructions = '';
      const agentStartIndex = lines.findIndex(l => l.startsWith('**Coding Agent Instructions**:'));
      if (agentStartIndex !== -1) {
        const agentLines = [];
        for (let j = agentStartIndex; j < lines.length; j++) {
          const l = lines[j];
          if (j > agentStartIndex && (l.startsWith('**') || l.startsWith('###') || l.startsWith('##'))) {
            break;
          }
          agentLines.push(l);
        }
        agentInstructions = agentLines.join('\n').replace(/^\*\*Coding Agent Instructions\*\*:\s*/i, '').trim();
      }

      issues.push({
        rank,
        title,
        impact,
        category,
        affected,
        measured,
        problem,
        recommendedFix,
        agentInstructions
      });
    }
  }

  // Helper to extract list items from a section by header regex
  function extractListItems(headerRegex) {
    const list = [];
    const sectionMatch = markdown.match(new RegExp(`${headerRegex}[\\s\\S]*?(?=## \\d|$)`, 'i'));
    if (sectionMatch) {
      const lines = sectionMatch[0].split('\n');
      lines.forEach(line => {
        const bulletMatch = line.match(/^\s*[-\*\d\.]+\s+(.+)$/);
        if (bulletMatch) {
          list.push(bulletMatch[1].trim());
        }
      });
    }
    return list;
  }

  // Extract Concrete Fixes (combined from Easy Wins, Medium Fixes, Hard Fixes)
  const concreteFixes = [
    ...extractListItems('## 4\\. Easy Wins'),
    ...extractListItems('## 5\\. Medium Fixes'),
    ...extractListItems('## 6\\. Hard')
  ];

  // Extract Guardrails
  const guardrails = extractListItems('## 9\\. Guardrails');

  // Extract Acceptance Criteria
  const acceptanceCriteria = extractListItems('## 10\\. Acceptance Criteria');

  return {
    url,
    date,
    strategies,
    issues,
    concreteFixes,
    guardrails,
    acceptanceCriteria
  };
}
