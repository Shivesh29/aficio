/**
 * Cloudflare Worker: Aficio Smart Reverse Proxy & Offline Schedule Fallback
 * 
 * Behavior:
 * 1. Forwards incoming requests to the live Azure VM (20.219.11.69).
 * 2. If the VM is active and running -> passes traffic through seamlessly.
 * 3. If the VM is sleeping/deallocated (Connection refused / 521 Origin Down) ->
 *    instantly returns the branded Aficio Scheduled Off-Hours page (<20ms).
 */

const OFFLINE_HTML = `__OFFLINE_HTML_PLACEHOLDER__`;

export default {
  async fetch(request, env, ctx) {
    const originUrl = new URL(request.url);
    // Point origin to Azure VM public IP or internal hostname
    originUrl.hostname = '20.219.11.69';

    const originRequest = new Request(originUrl.toString(), {
      method: request.method,
      headers: request.headers,
      body: request.body,
      redirect: 'manual',
    });

    try {
      // Set a 4-second timeout so offline visitors don't wait on a dead TCP socket
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);

      const response = await fetch(originRequest, {
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      // Cloudflare error statuses indicating origin VM is stopped/unreachable
      if (response.status === 521 || response.status === 522 || response.status === 523) {
        return new Response(OFFLINE_HTML, {
          status: 200,
          headers: {
            'Content-Type': 'text/html; charset=utf-8',
            'Cache-Control': 'no-cache, no-store, must-revalidate',
          },
        });
      }

      return response;
    } catch (err) {
      // Origin VM is deallocated / TCP connection refused
      return new Response(OFFLINE_HTML, {
        status: 200,
        headers: {
          'Content-Type': 'text/html; charset=utf-8',
          'Cache-Control': 'no-cache, no-store, must-revalidate',
        },
      });
    }
  },
};

