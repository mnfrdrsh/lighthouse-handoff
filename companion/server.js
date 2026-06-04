import http from 'node:http';

const PORT = 31313;

const mockAnalysis = {
  executiveSummary: "Mock Liquid companion analyzed the Lighthouse report successfully.",
  quickWins: [
    "Review the highest-impact Lighthouse opportunities first.",
    "Rerun Lighthouse after each major fix."
  ],
  priorityFixes: [
    {
      id: "mock-fix",
      title: "Mock Priority Fix",
      reasoning: "This is returned from the local companion server to validate the integration path.",
      instructions: [
        "Confirm the extension can reach localhost.",
        "Confirm the returned AIAnalysis renders into Markdown.",
        "Confirm copy/download still works."
      ]
    }
  ],
  acceptanceCriteria: [
    "Extension health check shows Liquid Companion connected.",
    "Generated Markdown uses the companion response.",
    "No fallback to MockProvider occurs when companion is running."
  ],
  limitations: [
    "This is a mock server and does not run a real Liquid model yet."
  ]
};

const server = http.createServer((req, res) => {
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  // Handle preflight requests
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Health check endpoint
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: "ok",
      provider: "mock-liquid",
      model: "mock-lfm2.5-350m",
      ready: true
    }));
    return;
  }

  // Analyze endpoint
  if (req.method === 'POST' && req.url === '/analyze') {
    let body = '';
    req.on('data', chunk => {
      body += chunk.toString();
    });
    req.on('end', () => {
      try {
        const payload = JSON.parse(body);
        console.log(`[POST /analyze] Received request for ${payload.report?.url || 'unknown URL'} (Mode: ${payload.outputMode})`);
        
        // Return the mock AIAnalysis
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(mockAnalysis));
      } catch (err) {
        console.error('[POST /analyze] Error parsing request body', err);
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid JSON body' }));
      }
    });
    return;
  }

  // 404 for other routes
  res.writeHead(404);
  res.end('Not Found');
});

server.listen(PORT, () => {
  console.log(`\n========================================`);
  console.log(`🚀 Mock Liquid Companion Server Running`);
  console.log(`   http://localhost:${PORT}`);
  console.log(`========================================\n`);
  console.log(`Endpoints:`);
  console.log(`  GET  /health`);
  console.log(`  POST /analyze\n`);
  console.log(`Waiting for requests from Lighthouse Handoff extension...`);
});
