// api/psi-client.js
// Clean wrapper around Google PageSpeed Insights API

const PSI_ENDPOINT = 'https://www.googleapis.com/pagespeedonline/v5/runPagespeed';

/**
 * Run PageSpeed Insights for a single strategy.
 *
 * @param {Object} params
 * @param {string} params.url - The URL to audit (must be http/https)
 * @param {'mobile'|'desktop'} params.strategy
 * @param {string[]} params.categories - e.g. ['performance', 'accessibility']
 * @param {string} params.apiKey
 * @returns {Promise<Object>} The full PSI response
 */
export async function runPSI({ url, strategy, categories, apiKey }) {
  if (!url) throw new Error('URL is required');
  if (!strategy) throw new Error('Strategy is required');
  if (!apiKey) throw new Error('API key is required');

  const params = new URLSearchParams({
    url: url,
    strategy: strategy,
    key: apiKey,
  });

  // PSI supports multiple category params
  categories.forEach((cat) => {
    params.append('category', cat);
  });

  const response = await fetch(`${PSI_ENDPOINT}?${params.toString()}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    let message = `PSI API error: ${response.status}`;

    try {
      const errorData = await response.json();
      if (errorData?.error?.message) {
        message = errorData.error.message;
      }
    } catch (_) {
      // ignore JSON parse errors
    }

    // Common friendly messages
    if (response.status === 429) {
      message = 'Rate limit exceeded. Please wait and try again, or use a different API key.';
    } else if (response.status === 400) {
      message = message.includes('Invalid')
        ? message
        : 'Invalid request. Check the URL and that the API key is valid.';
    } else if (response.status === 403) {
      message = 'API key invalid or not authorized for PageSpeed Insights.';
    }

    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return response.json();
}

/**
 * Run PSI for multiple strategies (runs in parallel).
 *
 * @param {Object} params
 * @param {string} params.url
 * @param {('mobile'|'desktop')[]} params.strategies
 * @param {string[]} params.categories
 * @param {string} params.apiKey
 * @returns {Promise<Object[]>} Array of results (one per strategy)
 */
export async function runPSIForStrategies({ url, strategies, categories, apiKey }) {
  const promises = strategies.map((strategy) =>
    runPSI({ url, strategy, categories, apiKey })
      .then((data) => ({ strategy, success: true, data }))
      .catch((error) => ({ strategy, success: false, error: error.message }))
  );

  return Promise.all(promises);
}
