from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from database import init_db, engine
from routers import auth, health_data, analysis
import os

limiter = Limiter(key_func=get_remote_address)

app = FastAPI(
    title="Health Tracker API",
    description="Backend for the HealthTracker Android app — stores wearable data, user-logged entries, and runs health pattern analysis.",
    version="1.0.0",
)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# CORS — allow the web dashboard and Android app
ALLOWED_ORIGINS = os.getenv(
    "ALLOWED_ORIGINS",
    "http://localhost:5173,http://localhost:3000,https://health-tracker-web.vercel.app",
).split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(health_data.router)
app.include_router(analysis.router)


@app.on_event("startup")
def on_startup():
    init_db()


@app.get("/health")
def health_check():
    # Reports the driver in use so a silent fallback to the ephemeral SQLite
    # file is visible, rather than only surfacing as data loss after a restart.
    # Dialect name only — never any part of the connection string.
    return {"status": "ok", "database": engine.dialect.name}
