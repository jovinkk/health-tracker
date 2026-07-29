from sqlalchemy import Column, Integer, String, Float, DateTime, JSON, ForeignKey, UniqueConstraint
from sqlalchemy.orm import declarative_base
from datetime import datetime, timezone

Base = declarative_base()


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class HealthEntry(Base):
    """Structured entry parsed from speech input or manual input."""
    __tablename__ = "health_entries"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    timestamp = Column(DateTime, nullable=False, default=lambda: datetime.now(timezone.utc))
    # Type: nutrition | stress | pain | mood | sleep | medication | exercise | note
    entry_type = Column(String, nullable=False)
    # Raw speech transcript
    raw_input = Column(String)
    # Gemini-parsed structured data
    data = Column(JSON, nullable=False)
    # Numeric value for correlation analysis (primary metric value)
    numeric_value = Column(Float)
    # Sub-category (e.g. "lower back" for pain, "anxiety" for stress)
    sub_category = Column(String)
    source = Column(String, default="speech")  # speech | manual | wearable

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "timestamp": self.timestamp.isoformat(),
            "entry_type": self.entry_type,
            "raw_input": self.raw_input,
            "data": self.data,
            "numeric_value": self.numeric_value,
            "sub_category": self.sub_category,
            "source": self.source,
        }


class WearableSnapshot(Base):
    """Snapshot of wearable sensor data synced from Android."""
    __tablename__ = "wearable_snapshots"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    timestamp = Column(DateTime, nullable=False)
    device_name = Column(String)
    steps = Column(Integer)
    heart_rate_avg = Column(Float)
    heart_rate_resting = Column(Float)
    hrv_ms = Column(Float)  # Heart Rate Variability
    spo2_pct = Column(Float)  # Blood oxygen %
    sleep_duration_min = Column(Integer)
    sleep_deep_min = Column(Integer)
    sleep_rem_min = Column(Integer)
    sleep_score = Column(Integer)
    calories_active = Column(Float)
    calories_total = Column(Float)
    stress_score = Column(Integer)  # Samsung Health stress (0-100)
    skin_temp_celsius = Column(Float)
    extra = Column(JSON)  # Any additional device-specific metrics

    __table_args__ = (
        UniqueConstraint("user_id", "timestamp", "device_name", name="uq_wearable_user_ts_device"),
    )

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "timestamp": self.timestamp.isoformat(),
            "device_name": self.device_name,
            "steps": self.steps,
            "heart_rate_avg": self.heart_rate_avg,
            "heart_rate_resting": self.heart_rate_resting,
            "hrv_ms": self.hrv_ms,
            "spo2_pct": self.spo2_pct,
            "sleep_duration_min": self.sleep_duration_min,
            "sleep_deep_min": self.sleep_deep_min,
            "sleep_rem_min": self.sleep_rem_min,
            "sleep_score": self.sleep_score,
            "calories_active": self.calories_active,
            "calories_total": self.calories_total,
            "stress_score": self.stress_score,
            "skin_temp_celsius": self.skin_temp_celsius,
            "extra": self.extra,
        }
