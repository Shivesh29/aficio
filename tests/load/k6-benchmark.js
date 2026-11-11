import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// Custom metrics for granular observability
const playbackCacheLatency = new Trend('playback_cache_latency_ms');
const reviewsLatency = new Trend('reviews_latency_ms');
const albumMetadataLatency = new Trend('album_metadata_latency_ms');
const errorRate = new Rate('custom_error_rate');
const requestCounter = new Counter('total_custom_requests');

// Load profile configuration
export const options = {
  stages: [
    { duration: '10s', target: 20 },  // Warm up: ramp from 1 to 20 VUs
    { duration: '30s', target: 50 },  // Sustained load: 50 concurrent virtual users
    { duration: '20s', target: 100 }, // Peak burst: 100 concurrent virtual users
    { duration: '10s', target: 0 },   // Cool down: ramp down to 0
  ],
  thresholds: {
    // 95% of requests must complete under 1.2s across public HTTPS under 100 concurrent users
    http_req_duration: ['p(95)<1200', 'p(99)<2000'],
    // Error rate must stay below 1% (excluding intentional 429 rate limits)
    custom_error_rate: ['rate<0.01'],
    // Playback cache hits must stay under 500ms P95 across public network
    playback_cache_latency_ms: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'https://aficio.shivesh29.me';

export default function () {
  const rand = Math.random();

  // 1. (35% probability) Playback Cache Hit - tests Redis caching and DB indexing
  if (rand < 0.35) {
    const trackId = Math.random() < 0.5 ? 1322 : 1323; // About a Girl / Come As You Are
    const res = http.get(`${BASE_URL}/tracks/${trackId}/playback`, {
      tags: { name: 'GetTrackPlayback' },
    });
    playbackCacheLatency.add(res.timings.duration);
    requestCounter.add(1);

    const success = check(res, {
      'playback status is 200': (r) => r.status === 200,
      'playback has youtubeVideoId': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.youtubeVideoId && body.source === 'cached';
        } catch {
          return false;
        }
      },
    });
    errorRate.add(!success);
  }
  // 2. (30% probability) Album Metadata & Tracklist - tests Spring Gateway & Catalog Service
  else if (rand < 0.65) {
    const albumId = Math.random() < 0.5 ? 2 : 134; // Loveless or MTV Unplugged
    const res = http.get(`${BASE_URL}/albums/${albumId}`, {
      tags: { name: 'GetAlbumDetail' },
    });
    albumMetadataLatency.add(res.timings.duration);
    requestCounter.add(1);

    const success = check(res, {
      'album detail status is 200': (r) => r.status === 200,
      'album detail has title': (r) => {
        try {
          const body = JSON.parse(r.body);
          return Boolean(body.title);
        } catch {
          return false;
        }
      },
    });
    errorRate.add(!success);

    // Concurrently fetch tracklist
    const trackRes = http.get(`${BASE_URL}/tracks?albumId=${albumId}`, {
      tags: { name: 'GetAlbumTracks' },
    });
    check(trackRes, {
      'tracks status is 200': (r) => r.status === 200,
    });
  }
  // 3. (25% probability) Social Reviews & Comments - tests Social Service & PostgreSQL joins
  else if (rand < 0.90) {
    const albumId = Math.random() < 0.5 ? 2 : 134;
    const res = http.get(`${BASE_URL}/api/reviews/album/${albumId}`, {
      tags: { name: 'GetAlbumReviews' },
    });
    reviewsLatency.add(res.timings.duration);
    requestCounter.add(1);

    const success = check(res, {
      'reviews status is 200': (r) => r.status === 200,
      'reviews is array': (r) => {
        try {
          return Array.isArray(JSON.parse(r.body));
        } catch {
          return false;
        }
      },
    });
    errorRate.add(!success);
  }
  // 4. (10% probability) User Profile & Public Collections (Auth Service - Protected by 100 req/min rate limit)
  else {
    const username = 'elena_vinyl';
    const res = http.get(`${BASE_URL}/users/${username}`, {
      tags: { name: 'GetUserProfile' },
    });
    requestCounter.add(1);

    const success = check(res, {
      'profile status is 200 (or 429 rate-limited after 100 reqs/min)': (r) =>
        r.status === 200 || r.status === 429,
      'profile payload valid when 200': (r) => {
        if (r.status === 429) return true; // Rate limiting actively protecting auth-service
        try {
          return JSON.parse(r.body).username === username;
        } catch {
          return false;
        }
      },
    });
    errorRate.add(!success);
  }

  // Realistic human pacing between requests (100ms - 500ms)
  sleep(Math.random() * 0.4 + 0.1);
}

