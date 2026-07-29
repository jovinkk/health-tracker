# Health Tracker — Setup Guide

## Project Structure

```
health-tracker/
├── backend/    FastAPI Python API
├── web/        React + Vite dashboard
└── android/    Android Kotlin app
```

---

## 1. Backend (FastAPI)

### Local development

**Requirements:** Python 3.11+

```bash
cd backend

# Create virtual environment
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Copy env file
cp .env.example .env

# Run
uvicorn main:app --reload --port 8000
```

The API docs will be at: http://localhost:8000/docs

### Deploy to Render.com (free)

1. Create account at https://render.com
2. New → Web Service → connect your GitHub repo
3. Set:
   - **Root directory:** `backend`
   - **Build command:** `pip install -r requirements.txt`
   - **Start command:** `uvicorn main:app --host 0.0.0.0 --port $PORT`
4. Add environment variables:
   - `SECRET_KEY` → generate a random string (e.g. `python -c "import secrets; print(secrets.token_hex(32))"`)
   - `ALLOWED_ORIGINS` → your Vercel URL once deployed
5. Add a **Disk** (persistent storage):
   - Mount path: `/data`
   - Update `DATABASE_URL` env var to: `sqlite:////data/health_tracker.db`

Your backend URL will be: `https://your-app.onrender.com`

---

## 2. Web Dashboard (React)

### Local development

**Requirements:** Node.js 18+

```bash
cd web
npm install

# Set API URL
cp .env.example .env.local
# Edit .env.local → VITE_API_URL=http://localhost:8000

npm run dev
```

Dashboard at: http://localhost:5173

### Deploy to Vercel (free)

1. Push the project to GitHub
2. Go to https://vercel.com → New Project → import your repo
3. Set **Root Directory** to `web`
4. Add environment variable: `VITE_API_URL=https://your-app.onrender.com`
5. Deploy

Your web URL will be: `https://your-app.vercel.app`

After deploying, go back to Render and update `ALLOWED_ORIGINS` to include your Vercel URL.

---

## 3. Android App

### Requirements

- **Android Studio** Hedgehog (2023.1) or later
  - Download: https://developer.android.com/studio
- **JDK 17** (bundled with Android Studio)
- **Android device or emulator** running Android 9+ (API 28+)

### Steps

1. Open Android Studio → **Open** → select the `android/` folder
2. Wait for Gradle sync to complete

3. **Set your API keys** in [app/build.gradle.kts](android/app/build.gradle.kts):
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"https://your-app.onrender.com\"")
   buildConfigField("String", "GEMINI_API_KEY", "\"your-gemini-api-key-here\"")
   ```
   Get your Gemini API key at: https://aistudio.google.com/app/apikey

4. **Build & Run:**
   - Connect your Galaxy Watch's paired phone via USB (or use emulator)
   - Click **Run** (▶) in Android Studio

5. **Grant permissions when prompted:**
   - Go to **Settings** tab → "Grant Health Connect Permissions"
   - Log in with your backend credentials (create account first)

### Building a release APK

```
Build → Generate Signed Bundle/APK → APK → create/use keystore → Release
```

The APK will be at: `app/release/app-release.apk`

---

## 4. Health Connect Setup (Galaxy Watch)

Samsung Health automatically writes data to Health Connect if you:
1. Install **Samsung Health** on your phone (already installed on Galaxy phones)
2. Install **Health Connect** from Play Store if not already present
3. Open Samsung Health → Settings → Connected services → Health Connect → Allow all

HealthTracker reads: steps, heart rate, HRV, SpO₂, sleep stages, calories.

---

## 5. Using the App

### Speech Widget
1. Long-press your home screen → Widgets → HealthTracker → add **Log Health** widget
2. Tap the mic button → speak naturally:
   - *"Lower back pain, about a 6 out of 10, sharp when I stand up"*
   - *"Stress level 8, feeling overwhelmed by work deadlines"*
   - *"Had oatmeal and a banana for breakfast, maybe 400 calories"*
   - *"Slept 7.5 hours last night but woke up twice, feel groggy"*
   - *"Took 400mg ibuprofen for a tension headache"*
3. Gemini parses the speech → structured entry saved automatically

### In-app logging
- Open the app → **Log** tab → tap the mic FAB button

### Viewing insights
- **Dashboard:** today's wearable metrics (auto-synced every 6 hours)
- **Insights:** health pattern alerts backed by scientific literature
- **Correlations:** visit the web dashboard for scatter plot analysis

---

## Gemini API Key

1. Go to https://aistudio.google.com/app/apikey
2. Create API key (free tier: 15 requests/minute, 1,500/day)
3. Paste into `android/app/build.gradle.kts` → `GEMINI_API_KEY`

---

## Architecture Summary

```
Galaxy Watch
    ↓ Samsung Health
Health Connect API
    ↓
Android App (Kotlin)
    ├── Reads wearable data (HealthConnectManager)
    ├── Processes speech via Gemini API
    ├── Stores all data in Room (SQLite)
    └── Syncs to Backend every 6h (WorkManager)
         ↓
FastAPI Backend (Render.com)
    ├── Stores in SQLite
    ├── Correlation analysis (Pearson r via scipy)
    └── Pattern detection (7 health risk patterns)
         ↓
React Web Dashboard (Vercel)
    ├── Dashboard with metric charts
    ├── Log history with filters
    ├── Correlation scatter plots
    └── Insights with science references
```
