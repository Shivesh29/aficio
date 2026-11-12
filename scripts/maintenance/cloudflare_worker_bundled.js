/**
 * Cloudflare Worker: Aficio Smart Reverse Proxy & Offline Schedule Fallback
 * 
 * Behavior:
 * 1. Forwards incoming requests to the live Azure VM (20.219.11.69).
 * 2. If the VM is active and running -> passes traffic through seamlessly.
 * 3. If the VM is sleeping/deallocated (Connection refused / 521 Origin Down) ->
 *    instantly returns the branded Aficio Scheduled Off-Hours page (<20ms).
 */

const OFFLINE_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Aficio - Scheduled Off-Hours</title>
  <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23e6a147' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><circle cx='12' cy='12' r='3'/></svg>">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;600&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg-base: #121212;
      --bg-card: #181818;
      --bg-card-hover: #1f1f1f;
      --border-subtle: rgba(255, 255, 255, 0.08);
      --border-medium: rgba(255, 255, 255, 0.14);
      --text-main: #ffffff;
      --text-muted: #a3a3a3;
      --text-faint: #666666;
      --accent-gold: #e6a147;
      --accent-gold-hover: #ffb854;
      --accent-gold-glow: rgba(230, 161, 71, 0.15);
      --accent-amber: #f59e0b;
      --font-sans: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
      --font-mono: 'JetBrains Mono', monospace;
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }

    body {
      background-color: var(--bg-base);
      color: var(--text-main);
      font-family: var(--font-sans);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2rem 1.25rem;
      position: relative;
      overflow-x: hidden;
      background-image: 
        radial-gradient(circle at 50% 25%, rgba(230, 161, 71, 0.08) 0%, transparent 60%),
        radial-gradient(circle at 80% 80%, rgba(245, 158, 11, 0.03) 0%, transparent 50%);
    }

    .maintenance-container {
      width: 100%;
      max-width: 620px;
      margin: 0 auto;
      text-align: center;
      position: relative;
      z-index: 2;
    }

    /* Floating Disc Badge */
    .disc-badge-wrap {
      position: relative;
      width: 68px;
      height: 68px;
      margin: 0 auto 1.5rem;
    }

    .disc-badge {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      background: linear-gradient(135deg, #1f1b14 0%, #0d0c0a 100%);
      border: 1px solid rgba(230, 161, 71, 0.35);
      box-shadow: 0 0 35px var(--accent-gold-glow), 0 10px 25px rgba(0, 0, 0, 0.5);
      display: grid;
      place-items: center;
      animation: gentleSpin 24s linear infinite;
    }

    .disc-badge svg {
      width: 32px;
      height: 32px;
      stroke: var(--accent-gold);
      stroke-width: 1.8;
    }

    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      background: rgba(245, 158, 11, 0.12);
      border: 1px solid rgba(245, 158, 11, 0.35);
      color: var(--accent-amber);
      padding: 0.35rem 0.85rem;
      border-radius: 9999px;
      font-size: 0.74rem;
      font-weight: 700;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      margin-bottom: 1rem;
    }

    .status-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background-color: var(--accent-amber);
      box-shadow: 0 0 8px var(--accent-amber);
      animation: pulseDot 2s ease-in-out infinite;
    }

    h1 {
      font-size: 2rem;
      font-weight: 800;
      letter-spacing: -0.02em;
      margin-bottom: 0.75rem;
      color: #ffffff;
      line-height: 1.25;
    }

    .lead-text {
      color: var(--text-muted);
      font-size: 0.95rem;
      line-height: 1.6;
      margin-bottom: 2rem;
      max-width: 520px;
      margin-left: auto;
      margin-right: auto;
    }

    /* Main Card */
    .schedule-card {
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      border-radius: 16px;
      padding: 1.75rem 2rem;
      box-shadow: 0 20px 45px rgba(0, 0, 0, 0.65);
      margin-bottom: 1.5rem;
      text-align: left;
    }

    /* Countdown Display */
    .countdown-section {
      background: rgba(255, 255, 255, 0.02);
      border: 1px solid var(--border-subtle);
      border-radius: 12px;
      padding: 1.25rem 1rem;
      margin-bottom: 1.5rem;
      text-align: center;
    }

    .countdown-label {
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--text-faint);
      margin-bottom: 0.6rem;
    }

    .countdown-timer {
      font-family: var(--font-mono);
      font-size: 2.2rem;
      font-weight: 700;
      color: var(--accent-gold);
      letter-spacing: 0.08em;
      display: flex;
      justify-content: center;
      align-items: baseline;
      gap: 0.4rem;
    }

    .countdown-unit-wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      min-width: 58px;
    }

    .countdown-unit-val {
      line-height: 1;
    }

    .countdown-unit-lbl {
      font-family: var(--font-sans);
      font-size: 0.65rem;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      margin-top: 0.35rem;
      letter-spacing: 0.05em;
    }

    .countdown-separator {
      color: rgba(255, 255, 255, 0.25);
      font-size: 1.75rem;
      line-height: 1;
      transform: translateY(-4px);
    }

    /* Timetable Grid */
    .timetable-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
      margin-bottom: 0.5rem;
    }

    .timetable-item {
      padding: 0.85rem 1rem;
      background: rgba(255, 255, 255, 0.025);
      border: 1px solid var(--border-subtle);
      border-radius: 10px;
    }

    .timetable-item-status {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 0.35rem;
    }

    .status-active-label {
      color: #10b981;
    }

    .status-sleep-label {
      color: var(--accent-amber);
    }

    .timetable-time {
      font-size: 1.05rem;
      font-weight: 700;
      color: #ffffff;
      margin-bottom: 0.2rem;
    }

    .timetable-sub {
      font-size: 0.74rem;
      color: var(--text-muted);
    }

    /* Portfolio Recruiter Callout */
    .portfolio-card {
      background: rgba(230, 161, 71, 0.04);
      border: 1px solid rgba(230, 161, 71, 0.22);
      border-radius: 14px;
      padding: 1.5rem;
      text-align: left;
      margin-bottom: 2rem;
    }

    .portfolio-header {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      margin-bottom: 0.5rem;
    }

    .portfolio-header h3 {
      font-size: 0.95rem;
      font-weight: 700;
      color: #ffffff;
    }

    .portfolio-desc {
      font-size: 0.84rem;
      color: var(--text-muted);
      line-height: 1.55;
      margin-bottom: 1.25rem;
    }

    .tech-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 0.45rem;
      margin-bottom: 1.25rem;
    }

    .tech-tag {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border-subtle);
      padding: 0.25rem 0.55rem;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 600;
      color: #d1d5db;
    }

    .portfolio-actions {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.7rem 1.2rem;
      font-size: 0.85rem;
      font-weight: 700;
      border-radius: 8px;
      text-decoration: none;
      transition: all 0.2s ease;
      cursor: pointer;
    }

    .btn-gold {
      background-color: var(--accent-gold);
      color: #121212;
      border: 1px solid transparent;
    }

    .btn-gold:hover {
      background-color: var(--accent-gold-hover);
      transform: translateY(-1px);
    }

    .btn-outline {
      background: transparent;
      color: #ffffff;
      border: 1px solid var(--border-medium);
    }

    .btn-outline:hover {
      background: rgba(255, 255, 255, 0.05);
      border-color: rgba(255, 255, 255, 0.3);
      transform: translateY(-1px);
    }

    .page-footer {
      font-size: 0.78rem;
      color: var(--text-faint);
    }

    .page-footer a {
      color: var(--text-muted);
      text-decoration: none;
    }

    .page-footer a:hover {
      color: var(--accent-gold);
    }

    @keyframes gentleSpin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    @keyframes pulseDot {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.4; transform: scale(0.85); }
    }

    @media (max-width: 580px) {
      h1 { font-size: 1.65rem; }
      .timetable-grid { grid-template-columns: 1fr; }
      .countdown-timer { font-size: 1.8rem; }
      .countdown-unit-wrap { min-width: 46px; }
      .portfolio-actions { flex-direction: column; }
      .btn { width: 100%; justify-content: center; }
    }
  </style>
</head>
<body>

  <div class="maintenance-container">
    
    <!-- Badge -->
    <div class="disc-badge-wrap">
      <div class="disc-badge">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <circle cx="12" cy="12" r="10"/>
          <circle cx="12" cy="12" r="3"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke-opacity="0.3"/>
        </svg>
      </div>
    </div>

    <div class="status-pill">
      <span class="status-dot"></span>
      Scheduled Nightly Sleep
    </div>

    <h1>Aficio is Resting for the Night</h1>
    <p class="lead-text">
      To sustainably manage cloud resources and preserve student grant credits, our live microservices power down nightly and resume automatically every morning.
    </p>

    <!-- Schedule Card -->
    <div class="schedule-card">

      <!-- Live Dynamic Countdown -->
      <div class="countdown-section">
        <div class="countdown-label">Next Live Session In</div>
        <div class="countdown-timer" id="countdown">
          <div class="countdown-unit-wrap">
            <span class="countdown-unit-val" id="cd-hours">00</span>
            <span class="countdown-unit-lbl">Hours</span>
          </div>
          <span class="countdown-separator">:</span>
          <div class="countdown-unit-wrap">
            <span class="countdown-unit-val" id="cd-mins">00</span>
            <span class="countdown-unit-lbl">Minutes</span>
          </div>
          <span class="countdown-separator">:</span>
          <div class="countdown-unit-wrap">
            <span class="countdown-unit-val" id="cd-secs">00</span>
            <span class="countdown-unit-lbl">Seconds</span>
          </div>
        </div>
      </div>

      <!-- Timetable Grid -->
      <div class="timetable-grid">
        <div class="timetable-item">
          <div class="timetable-item-status status-active-label">
            &bull; Active Daily (14 hrs)
          </div>
          <div class="timetable-time">9:00 AM - 11:00 PM</div>
          <div class="timetable-sub">Indian Standard Time (IST)</div>
        </div>

        <div class="timetable-item">
          <div class="timetable-item-status status-sleep-label">
            &bull; Nightly Sleep (10 hrs)
          </div>
          <div class="timetable-time">11:00 PM - 9:00 AM</div>
          <div class="timetable-sub">Indian Standard Time (IST)</div>
        </div>
      </div>

    </div>

    <!-- Recruiter / Portfolio Callout -->
    <div class="portfolio-card">
      <div class="portfolio-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#e6a147" stroke-width="2">
          <path d="M16 18l6-6-6-6M8 6l-6 6 6 6"/>
        </svg>
        <h3>Reviewing This Project For An Open Role?</h3>
      </div>
      <p class="portfolio-desc">
        Aficio is a full-stack distributed music dossier and social discovery application built with Spring Boot microservices, React + TypeScript, PostgreSQL, Redis caching, and an automated dataset ETL pipeline that serves audio streaming at zero external API cost.
      </p>

      <div class="tech-tags">
        <span class="tech-tag">Java 21</span>
        <span class="tech-tag">Spring Boot 3</span>
        <span class="tech-tag">Spring Cloud Gateway</span>
        <span class="tech-tag">PostgreSQL</span>
        <span class="tech-tag">Redis</span>
        <span class="tech-tag">Docker Compose</span>
        <span class="tech-tag">React / TypeScript</span>
        <span class="tech-tag">Azure VM</span>
      </div>

      <div class="portfolio-actions">
        <a href="https://github.com/Shivesh29/aficio" target="_blank" rel="noopener noreferrer" class="btn btn-gold">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/>
          </svg>
          Explore Source Code
        </a>
        <a href="https://github.com/Shivesh29/aficio#readme" target="_blank" rel="noopener noreferrer" class="btn btn-outline">
          Architecture & System Design
        </a>
      </div>
    </div>

    <!-- Footer -->
    <div class="page-footer">
      Aficio &bull; Developed by <a href="https://github.com/Shivesh29" target="_blank" rel="noopener noreferrer">Shivesh Sharma</a> &bull; Hosted on Microsoft Azure
    </div>

  </div>

  <script>
    // Live countdown to next 9:00 AM IST (which is 03:30 UTC)
    function updateCountdown() {
      const now = new Date();
      
      // Target is today or tomorrow at 03:30:00 UTC
      const nextWake = new Date();
      nextWake.setUTCHours(3, 30, 0, 0);

      // If 03:30 UTC has already passed today, target tomorrow's 03:30 UTC
      if (now.getTime() >= nextWake.getTime()) {
        nextWake.setUTCDate(nextWake.getUTCDate() + 1);
      }

      const diffMs = nextWake.getTime() - now.getTime();
      const totalSecs = Math.max(0, Math.floor(diffMs / 1000));

      const hours = Math.floor(totalSecs / 3600);
      const minutes = Math.floor((totalSecs % 3600) / 60);
      const seconds = totalSecs % 60;

      const pad = (n) => String(n).padStart(2, '0');

      document.getElementById('cd-hours').textContent = pad(hours);
      document.getElementById('cd-mins').textContent = pad(minutes);
      document.getElementById('cd-secs').textContent = pad(seconds);
    }

    updateCountdown();
    setInterval(updateCountdown, 1000);
  </script>

</body>
</html>
`;

export default {
  async fetch(request, env, ctx) {
    try {
      // Forward request to origin (resolved via Cloudflare DNS to 20.219.11.69)
      const response = await fetch(request);

      // Cloudflare 521 (Web Server Down), 522 (Connection Timed Out), 523 (Origin Unreachable)
      if (response.status === 521 || response.status === 522 || response.status === 523 || response.status === 502 || response.status === 504) {
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
      // Origin unreachable or timeout
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

