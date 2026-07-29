from pydantic import BaseModel, field_validator
from typing import Optional, Any
from datetime import datetime


# ── Auth ──────────────────────────────────────────────────────────────────────

class UserCreate(BaseModel):
    username: str
    password: str


class UserOut(BaseModel):
    id: int
    username: str
    created_at: datetime

    class Config:
        from_attributes = True


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


# ── Health Entries ─────────────────────────────────────────────────────────────

class HealthEntryCreate(BaseModel):
    timestamp: Optional[datetime] = None
    entry_type: str  # nutrition | stress | pain | mood | sleep | medication | exercise | note
    raw_input: Optional[str] = None
    data: dict[str, Any]
    numeric_value: Optional[float] = None
    sub_category: Optional[str] = None
    source: Optional[str] = "speech"


class HealthEntryPatch(BaseModel):
    numeric_value: Optional[float] = None
    sub_category: Optional[str] = None
    data: Optional[dict[str, Any]] = None
    raw_input: Optional[str] = None


class HealthEntryOut(BaseModel):
    id: int
    user_id: int
    timestamp: datetime
    entry_type: str
    raw_input: Optional[str]
    data: dict[str, Any]
    numeric_value: Optional[float]
    sub_category: Optional[str]
    source: Optional[str]

    class Config:
        from_attributes = True


# ── Wearable Snapshots ─────────────────────────────────────────────────────────

class WearableSnapshotCreate(BaseModel):
    timestamp: datetime
    device_name: Optional[str] = None
    steps: Optional[int] = None
    heart_rate_avg: Optional[float] = None
    heart_rate_resting: Optional[float] = None
    hrv_ms: Optional[float] = None
    spo2_pct: Optional[float] = None
    sleep_duration_min: Optional[int] = None
    sleep_deep_min: Optional[int] = None
    sleep_rem_min: Optional[int] = None
    sleep_score: Optional[int] = None
    calories_active: Optional[float] = None
    calories_total: Optional[float] = None
    stress_score: Optional[int] = None
    skin_temp_celsius: Optional[float] = None
    extra: Optional[dict[str, Any]] = None


class WearableSnapshotOut(WearableSnapshotCreate):
    id: int
    user_id: int

    class Config:
        from_attributes = True


# ── Analysis ───────────────────────────────────────────────────────────────────

class CorrelationResult(BaseModel):
    metric1: str
    metric2: str
    pearson_r: float
    p_value: float
    n_samples: int
    interpretation: str


class PatternAlert(BaseModel):
    pattern_id: str
    title: str
    description: str
    severity: str  # info | warning | alert
    science_note: str
    days_observed: int
    data_points: list[dict[str, Any]]
