"""Correlation analysis between any two health metrics."""
from scipy import stats
from sqlalchemy.orm import Session
from datetime import datetime, timedelta, timezone
from typing import Optional
import models

# Metrics that are ordinal/subjective scales — use Spearman instead of Pearson
ORDINAL_METRICS = {"stress", "pain", "mood", "sleep_quality"}


METRIC_SOURCES = {
    # wearable metrics → column name on WearableSnapshot
    "steps": ("wearable", "steps"),
    "heart_rate_avg": ("wearable", "heart_rate_avg"),
    "heart_rate_resting": ("wearable", "heart_rate_resting"),
    "hrv_ms": ("wearable", "hrv_ms"),
    "spo2_pct": ("wearable", "spo2_pct"),
    "sleep_duration_min": ("wearable", "sleep_duration_min"),
    "sleep_deep_min": ("wearable", "sleep_deep_min"),
    "sleep_rem_min": ("wearable", "sleep_rem_min"),
    "sleep_score": ("wearable", "sleep_score"),
    "calories_active": ("wearable", "calories_active"),
    "stress_score_wearable": ("wearable", "stress_score"),
    # entry metrics → entry_type
    "stress": ("entry", "stress"),
    "pain": ("entry", "pain"),
    "mood": ("entry", "mood"),
    "nutrition_calories": ("entry", "nutrition"),
    "sleep_quality": ("entry", "sleep"),
}


def _fetch_wearable_series(db: Session, user_id: int, col: str, since: datetime) -> dict[str, float]:
    """Returns {date_str: value} for a wearable column."""
    rows = (
        db.query(models.WearableSnapshot)
        .filter(
            models.WearableSnapshot.user_id == user_id,
            models.WearableSnapshot.timestamp >= since,
        )
        .all()
    )
    buckets: dict[str, list[float]] = {}
    for row in rows:
        val = getattr(row, col)
        if val is not None:
            date_key = row.timestamp.date().isoformat()
            buckets.setdefault(date_key, []).append(float(val))
    return {k: sum(v) / len(v) for k, v in buckets.items()}


def _fetch_entry_series(db: Session, user_id: int, entry_type: str, since: datetime) -> dict[str, float]:
    """Returns {date_str: avg_numeric_value} for a health entry type."""
    rows = (
        db.query(models.HealthEntry)
        .filter(
            models.HealthEntry.user_id == user_id,
            models.HealthEntry.entry_type == entry_type,
            models.HealthEntry.timestamp >= since,
            models.HealthEntry.numeric_value.isnot(None),
        )
        .all()
    )
    agg: dict[str, list[float]] = {}
    for row in rows:
        key = row.timestamp.date().isoformat()
        agg.setdefault(key, []).append(row.numeric_value)
    return {k: sum(v) / len(v) for k, v in agg.items()}


def _interpret(r: float, p: float, n: int) -> str:
    if n < 7:
        return "Insufficient data (need at least 7 paired days)"
    if p > 0.05:
        return f"No significant correlation (r={r:.2f}, p={p:.3f})"
    strength = abs(r)
    direction = "positive" if r > 0 else "negative"
    if strength < 0.3:
        label = "weak"
    elif strength < 0.6:
        label = "moderate"
    else:
        label = "strong"
    return f"Statistically significant {label} {direction} correlation (r={r:.2f}, p={p:.3f})"


def compute_correlation(
    db: Session,
    user_id: int,
    metric1: str,
    metric2: str,
    days: int = 30,
) -> dict:
    since = datetime.now(timezone.utc) - timedelta(days=days)

    def fetch(metric: str) -> dict[str, float]:
        source, key = METRIC_SOURCES.get(metric, ("entry", metric))
        if source == "wearable":
            return _fetch_wearable_series(db, user_id, key, since)
        return _fetch_entry_series(db, user_id, key, since)

    series1 = fetch(metric1)
    series2 = fetch(metric2)

    # Align on common dates
    common_dates = sorted(set(series1) & set(series2))
    x = [series1[d] for d in common_dates]
    y = [series2[d] for d in common_dates]

    if len(x) < 3:
        return {
            "metric1": metric1,
            "metric2": metric2,
            "pearson_r": 0.0,
            "p_value": 1.0,
            "n_samples": len(x),
            "interpretation": "Insufficient overlapping data points",
            "pairs": [],
        }

    use_spearman = metric1 in ORDINAL_METRICS or metric2 in ORDINAL_METRICS
    if use_spearman:
        r, p = stats.spearmanr(x, y)
    else:
        r, p = stats.pearsonr(x, y)
    return {
        "metric1": metric1,
        "metric2": metric2,
        "pearson_r": round(float(r), 4),
        "p_value": round(float(p), 4),
        "n_samples": len(x),
        "method": "spearman" if use_spearman else "pearson",
        "interpretation": _interpret(float(r), float(p), len(x)),
        "pairs": [{"date": d, "x": x[i], "y": y[i]} for i, d in enumerate(common_dates)],
    }
