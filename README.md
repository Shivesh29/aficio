# Aficio — Music Social & Discovery Platform

<a href="https://aficio.shivesh29.me" target="_blank" rel="noopener noreferrer"><img src="https://img.shields.io/badge/Live%20Demo-aficio.shivesh29.me-blue?style=for-the-badge&logo=google-chrome" alt="Live Application" /></a>
[![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=for-the-badge&logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis)](https://redis.io/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3-black?style=for-the-badge&logo=apachekafka)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![Cloudflare](https://img.shields.io/badge/Cloudflare-Edge%20Worker-F38020?style=for-the-badge&logo=cloudflare)](https://www.cloudflare.com/)
[![Azure](https://img.shields.io/badge/Azure-Cloud%20VM-0078D4?style=for-the-badge&logo=microsoft-azure)](https://azure.microsoft.com/)

**Aficio** is a distributed, full-stack music discovery, review, and social streaming platform. Built on a resilient **microservices architecture** with **Spring Boot 3**, **Spring Cloud Gateway**, **Apache Kafka**, **Redis**, and a modern **React 19 + TypeScript** frontend, Aficio enables music enthusiasts to catalog albums, write rich reviews, stream audio, curate custom lists, and connect with friends via private Listener Codes.

Live Production URL: <a href="https://aficio.shivesh29.me" target="_blank" rel="noopener noreferrer"><strong>https://aficio.shivesh29.me</strong></a>

---

## 🏛️ System Architecture

```mermaid
flowchart TD
    User["🌐 User Browser / Mobile Client"] -->|HTTPS / 443| CF["Cloudflare Edge Proxy<br/>• DDoS & Bot Protection<br/>• Smart Offline Fallback (&lt;20ms)"]
    
    subgraph EdgeTier ["Edge & Security Tier"]
        CF -->|Proxy Traffic| Nginx["Nginx Reverse Proxy (:443)<br/>• SSL Termination (Let's Encrypt)<br/>• Burst Rate Limiting (limit_req)<br/>• Security Headers & Static Bundles"]
    end
    
    subgraph FrontendTier ["Frontend Tier"]
        Nginx -->|Serves Static SPA| ReactApp["React 19 + TypeScript (Vite)<br/>• Custom Dark/Gold Design System<br/>• Global Audio Stream State Machine<br/>• iPhone Safe-Area Responsive Dock"]
    end
    
    subgraph GatewayTier ["API Gateway"]
        Nginx -->|Proxy REST & WebSockets| Gateway["Spring Cloud Gateway (:8080)<br/>• Central Routing & CORS Whitelist<br/>• Stateless JWT Authentication Filter<br/>• WebSocket (STOMP) Reverse Proxy"]
    end
    
    subgraph MicroservicesTier ["Microservices Tier"]
        Gateway -->|/users/**, /auth/**| AuthSvc["Auth Service (:8081)<br/>• BCrypt Password Hashing with Salts<br/>• 2FA TOTP (RFC 6238)<br/>• Cryptographic Listener Codes (AF-XXXX-XXXX)<br/>• Email Verification via Resend"]
        Gateway -->|/catalog/**| CatalogSvc["Catalog Service (:8082)<br/>• 12.6M Pre-Indexed Catalog Search<br/>• 3-Tier Offline-First Resolution Engine<br/>• History Recording & Cache-Aside Layer"]
        Gateway -->|/api/reviews/**, /api/messages/**| SocialSvc["Social Service (:8083)<br/>• 5-Star Reviews & Nested Comments<br/>• Curated Public/Private Playlists<br/>• STOMP WebSocket 1-on-1 Direct Messages<br/>• Real-Time Activity Feed & Follow Requests"]
    end

    subgraph CachingTier ["Caching & Event Streaming"]
        CatalogSvc ---|Sub-3ms Cache Hits| RedisCache[("Redis 7 In-Memory Cache<br/>• Playback Cache-Aside Layer<br/>• Real-Time Unread Badges")]
        CatalogSvc -->|Publish Album View Event| KafkaBus{{"Apache Kafka Event Bus<br/>• Topic: album-views<br/>• Topic: social-events"}}
        KafkaBus -->|Async Non-Blocking Consumer| SocialSvc
    end

    subgraph PersistenceTier ["Persistence Tier (PostgreSQL)"]
        AuthSvc --> AuthDB[("auth_db<br/>• Users & Credentials")]
        CatalogSvc --> CatalogDB[("catalog_db<br/>• 12.6M youtube_catalog Index<br/>• track_playback_cache")]
        SocialSvc --> SocialDB[("social_db<br/>• Reviews, Follows, Collections<br/>(13 Flyway Migrations)")]
    end
```

---

## ✨ Key Features

* **Zero-Quota Offline-First Audio Streaming:**
  * Powered by a pre-indexed **12.6-million-track database** (LAION-DISCO dataset).
  * 3-tier local resolution engine: **Redis Cache hit ($<3\text{ms}$)** $\rightarrow$ **12.6M Catalog Query ($<8\text{ms}$)** $\rightarrow$ **Cross-Album Studio Fallback**. Zero external third-party API dependencies or quota limits.
* **Music Graph & Catalog Exploration:** Integrated discographies, release groups, and high-resolution album art with phonetic transliteration matching.
* **Privacy by Default & Listener Codes (`AF-XXXX-XXXX`):**
  * Accounts are private by default and completely unlisted from public search to prevent enumeration scraping.
  * Users connect and share profiles exclusively via cryptographic, random 10-character Listener Codes.
* **Two-Factor Authentication (2FA / TOTP):** Optional RFC 6238 time-based authenticator app integration with QR code provisioning.
* **Ratings & Rich Reviews:** 5-star scoring system with markdown critiques, community likes, and nested discussions.
* **Curated Music Lists:** Custom tracklists and album dossiers with public vs. private visibility toggles.
* **Real-Time Direct Messaging (STOMP WebSockets):** Low-latency 1-on-1 messaging featuring read receipts, dynamic unread badges, and UTC timezone normalization.
* **Smart Edge Fallback & Sustainable Cloud Scheduling:**
  * Automated cloud operating schedule (**9:00 AM – 11:00 PM IST**), preserving cloud grant credits to achieve 5+ months of continuous runtime.
  * When the origin server powers down at night, a **Cloudflare Serverless Edge Worker** immediately intercepts connection timeouts and serves a styled off-hours status page with a real-time countdown timer to the next live session.

---

## 🛠️ Tech Stack & Engineering Highlights

| Layer | Technologies |
|:---|:---|
| **Frontend** | React 19, TypeScript, Vite, React Router 7, Lucide Icons, Custom CSS Design System |
| **Backend** | Java 21, Spring Boot 3.4.1, Spring Cloud Gateway, Spring Data JPA, Spring Security |
| **Event Bus & Messaging** | Apache Kafka 3 (Event-Driven Decoupling), STOMP over SockJS/WebSockets |
| **Databases & Cache** | PostgreSQL 16 (Multi-Schema Pattern: 12.6M+ indexed records), Redis 7 (Alpine), Flyway DB Migrations |
| **Security & Auth** | JWT (HMAC-SHA256), BCrypt Salting, RFC 6238 TOTP 2FA, Nginx Rate Limiting (`limit_req_zone`) |
| **DevOps & Cloud** | Docker Compose, Azure Linux VM (Central India), Cloudflare Edge Workers, Nginx, Let's Encrypt (Certbot), GitHub Actions |

---

## 💡 Engineering Deep Dives

### 1. Zero-Quota 12.6M Track Resolution Engine
Rather than relying on brittle third-party streaming APIs that enforce strict rate limits and break at scale, Aficio indexes **12,684,862 pre-crawled tracks** directly in PostgreSQL. Using B-Tree indexed foreign-key lookups combined with a Redis Cache-Aside layer, playback resolution takes **under 3 milliseconds** on cache hits and under 8 milliseconds on cold catalog queries.

### 2. Edge Fallback via Cloudflare Serverless Workers
To sustainably manage cloud resources on a student grant without sacrificing portfolio reliability, the Azure VM operates on a scheduled 14-hour daily window (9:00 AM to 11:00 PM IST). A Cloudflare Worker sits in front of the origin IP:
- **During active hours:** Proxies traffic seamlessly to the live application.
- **During nightly sleep:** Detects the offline server and immediately returns an optimized maintenance page with a live JavaScript countdown timer and recruiter dossier in under 20ms.

### 3. Asynchronous Event Decoupling with Apache Kafka
Social actions (such as album views, follower notifications, and review likes) are published as immutable domain events to Kafka topics (`album-views`, `social-events`). This prevents downstream database writes from blocking real-time audio streaming.

### 4. Defense-in-Depth Security
- **Anti-Scraping Privacy:** Usernames are unlisted; accounts are addressable only via random 10-character Listener Codes (`AF-XXXX-XXXX`).
- **Nginx Rate Limiting:** Sensitive endpoints (`/users/login`, `/users/register`) are throttled with a burst buffer of 10 requests, neutralizing brute-force and credential-stuffing attacks.
- **Network Isolation:** PostgreSQL and Redis containers do not expose public ports to the internet; they are accessible only via the internal Docker bridge network (`aficio-net`).
- **Automated Database Backup Rotation:** Nightly database dumps execute at 10:30 PM IST with automated 7-day archive pruning.

---

## 🛠️ Local Development & Orchestration

The local Docker Compose configuration boots the complete microservice mesh, Kafka event broker, Redis cache, and multi-schema PostgreSQL persistence tier in isolated bridge networking.

> [!NOTE]
> **Production vs. Local Catalog:** The complete **12.6M-track indexed catalog** and audio streaming engine are hosted on the live [Production Deployment](https://aficio.shivesh29.me). Fresh local environments boot with migrated schemas and can be optionally hydrated with synthetic community test data.

### Spin-up

```bash
# 1. Initialize environment variables with local defaults
cp .env.example .env

# 2. Build and boot all microservices, Kafka, Redis & PostgreSQL
docker compose up -d --build

# 3. (Optional) Hydrate with 15 synthetic music personas, reviews & listening history
./scripts/seed/apply_seed.sh
```

### Local Service Endpoints

| Service | Address | Description |
|:---|:---|:---|
| **Frontend Client** | [http://localhost:5174](http://localhost:5174) | React 19 SPA (Vite) |
| **Spring Cloud Gateway** | [http://localhost:8080](http://localhost:8080) | Single entry point & JWT filter |
| **Auth Service** | `http://localhost:8081` | BCrypt, TOTP 2FA, Listener Codes |
| **Catalog Service** | `http://localhost:8082` | Resolution engine & track index |
| **Social Service** | `http://localhost:8083` | Reviews, lists & STOMP WebSockets |
| **PostgreSQL 16** | `localhost:5432` | Multi-DB (`auth_db`, `catalog_db`, `social_db`) |
| **Redis 7** | `localhost:6379` | In-memory cache & Pub/Sub |
| **Apache Kafka** | `localhost:9092` | Event broker (`album-views`, `social-events`) |

To monitor logs or tear down the stack:
```bash
docker compose logs -f      # Follow unified cluster logs
docker compose down         # Gracefully terminate all containers
```

---

## 📂 Repository Structure

```text
aficio/
├── .github/workflows/          # GitHub Actions CI automation (Java & React test/build)
├── frontend/                   # React 19 + TypeScript + Vite Single Page Application
│   ├── src/
│   │   ├── api/                # Typed REST client and models
│   │   ├── components/         # Shared UI components (Navbar, Modals, MiniPlayer, AudioDock)
│   │   ├── context/            # AuthContext, PlayerContext global state providers
│   │   ├── pages/              # Catalog, AlbumDetail, Messages, Profile, Settings
│   │   └── utils/              # UTC-aware date utilities and formatters
├── services/
│   ├── api-gateway/            # Spring Cloud Gateway & JWT validation (Port 8080)
│   ├── auth-service/           # User auth, BCrypt, 2FA TOTP, Listener Codes, Resend (Port 8081)
│   ├── catalog-service/        # 12.6M catalog index, 3-tier playback resolution (Port 8082)
│   └── social-service/         # Reviews, lists, DMs, STOMP WebSockets, Kafka consumers (Port 8083)
├── shared/
│   ├── dto-lib/                # Shared Java records and DTO contracts
│   └── kafka-events/           # Shared event schemas and publishers
├── scripts/
│   ├── backup_databases.sh     # Automated nightly PostgreSQL backup and pruning script
│   ├── maintenance/            # Cloudflare Worker reverse proxy & offline fallback page
│   └── seed/                   # Realistic user, social, and catalog SQL generators
├── tests/load/                 # k6 load testing scripts (sustained 250+ concurrent VUs)
├── docker-compose.yml          # Production & local multi-container orchestration
└── init-dbs.sh                 # Database initialization script for multi-schema PostgreSQL
```

---

## 📄 License

Copyright &copy; 2026 Shivesh Sharma. All rights reserved.
