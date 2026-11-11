# Aficio Scheduled Off-Hours & Maintenance Setup

To maximize cloud credit longevity (preserving student/free grant credits until mid-April), Aficio runs on a scheduled daily window:

* **Active Window (14 hrs/day):** **9:00 AM to 11:00 PM IST** (03:30 – 17:30 UTC)
* **Nightly Sleep (10 hrs/day):** **11:00 PM to 9:00 AM IST** (17:30 – 03:30 UTC)
* **Cost Impact:** Cuts Azure compute consumption by **~42%**, extending credit runway by months.

---

## 1. The Offline / Scheduled Maintenance Page

* **Source File:** [`offline.html`](file:///c:/Users/sharm/OneDrive/Desktop/aficio/aficio/scripts/maintenance/offline.html)
* **Design Features:**
  * Matches Aficio's dark/gold aesthetic (`#121212`, Plus Jakarta Sans, spinning vinyl badge).
  * Real-time dynamic JavaScript countdown timer to the next 9:00 AM IST wake-up.
  * Clear operational schedule in IST and UTC.
  * Recruiter callout card detailing the Spring Boot microservices, Redis caching layer, Docker architecture, and direct links to the [GitHub Repository](https://github.com/Shivesh29/aficio).

---

## 2. Deploying via Free Cloudflare Worker (Zero Cost)

Because a deallocated Azure VM cannot respond to IP traffic, a free Cloudflare Worker acts as a reverse proxy:

1. **When VM is ON (9 AM – 11 PM IST):** Passes traffic directly to `20.219.11.69`.
2. **When VM is OFF (11 PM – 9 AM IST):** Catches the unreachable origin and immediately returns `offline.html` with valid SSL in under 20ms.

### Quick Setup Steps in Cloudflare:
1. In Cloudflare Dashboard, go to **Workers & Pages** -> **Create Application** -> **Create Worker**.
2. Name it `aficio-proxy`.
3. Replace the default code with the contents of [`cloudflare_worker_bundled.js`](file:///c:/Users/sharm/OneDrive/Desktop/aficio/aficio/scripts/maintenance/cloudflare_worker_bundled.js).
4. Click **Deploy**.
5. Under Worker Settings -> **Triggers** -> **Custom Domains**, add `aficio.shivesh29.me`.

---

## 3. Automating VM Sleep & Wakeup (Zero Manual Effort)

### Step A: Auto-Shutdown at 11:00 PM IST (Built into Azure)
1. Open [Azure Portal](https://portal.azure.com).
2. Go to your Virtual Machine (`aficio-vm`).
3. In the left sidebar under **Operations**, click **Auto-shutdown**.
4. Set:
   * **Enabled:** On
   * **Scheduled shutdown:** `23:00` (11:00 PM)
   * **Time zone:** `(UTC+05:30) Chennai, Kolkata, Mumbai, New Delhi`
   * Notification before shutdown: Optional (can be disabled or sent to your email).
5. Click **Save**.

### Step B: Auto-Startup at 9:00 AM IST
Azure VMs can be automatically started every morning at 9:00 AM IST using:
* **Option 1 (Azure Automation / Logic App):** A free consumption-tier Logic App running on recurrence (`At 09:00 AM India Time`) with the action `Start virtual machine`.
* **Option 2 (GitHub Actions Scheduled Workflow):** A cron workflow running `az vm start` at `30 3 * * *` (03:30 UTC).

