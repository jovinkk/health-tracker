"""
Scientifically-grounded pattern detection for adverse health/longevity indicators.
Each pattern has a science_note citing the mechanism and evidence base.
"""
from sqlalchemy.orm import Session
from datetime import datetime, timedelta, timezone
from typing import Optional
import models


def _last_n_days_wearable(db: Session, user_id: int, days: int) -> list[models.WearableSnapshot]:
    since = datetime.now(timezone.utc) - timedelta(days=days)
    return (
        db.query(models.WearableSnapshot)
        .filter(
            models.WearableSnapshot.user_id == user_id,
            models.WearableSnapshot.timestamp >= since,
        )
        .order_by(models.WearableSnapshot.timestamp)
        .all()
    )


def _last_n_days_entries(db: Session, user_id: int, entry_type: str, days: int) -> list[models.HealthEntry]:
    since = datetime.now(timezone.utc) - timedelta(days=days)
    return (
        db.query(models.HealthEntry)
        .filter(
            models.HealthEntry.user_id == user_id,
            models.HealthEntry.entry_type == entry_type,
            models.HealthEntry.timestamp >= since,
        )
        .order_by(models.HealthEntry.timestamp)
        .all()
    )


# ── Data hygiene ──────────────────────────────────────────────────────────────

# A tracker left on a desk still reports a handful of steps from being carried
# about, so require more than a trivial count before calling a day "worn".
MIN_STEPS_FOR_WEAR = 500


def _one_per_day(snapshots: list) -> list:
    """
    Collapse to a single snapshot per calendar day.

    The app appends a snapshot on each sync, so one day can carry several
    partial rows — a morning sync captures only that morning's steps. Detectors
    count entries, so without this a single day reads as several low ones.
    Keeps the richest row per day.
    """
    by_day: dict = {}
    for s in snapshots:
        key = s.timestamp.date()
        current = by_day.get(key)
        if current is None or (s.steps or -1) > (current.steps or -1):
            by_day[key] = s
    return [by_day[k] for k in sorted(by_day)]


def _was_worn(s) -> bool:
    """
    Whether there's evidence the device was actually worn that day.

    A day with no heart-rate, HRV or SpO2 reading and negligible steps is far
    more likely to be a day the tracker sat on a charger than a genuinely
    sedentary one. Counting those as inactivity produces alerts for days the
    user simply wasn't wearing anything.
    """
    if s.heart_rate_avg or s.heart_rate_resting or s.hrv_ms or s.spo2_pct:
        return True
    return (s.steps or 0) >= MIN_STEPS_FOR_WEAR


# ── Individual pattern detectors ──────────────────────────────────────────────

def check_sleep_deficit(snapshots: list) -> Optional[dict]:
    """Chronic sleep <7h is associated with increased all-cause mortality."""
    if len(snapshots) < 7:
        return None
    short_nights = [s for s in snapshots if s.sleep_duration_min and s.sleep_duration_min < 420]
    if len(short_nights) >= 5:
        avg = sum(s.sleep_duration_min for s in short_nights) / len(short_nights)
        return {
            "pattern_id": "sleep_deficit",
            "title": "Chronic Sleep Deficit",
            "description": f"{len(short_nights)} of the last {len(snapshots)} nights averaged {avg/60:.1f}h sleep (below the 7h threshold).",
            "severity": "warning" if avg >= 360 else "alert",
            "science_note": "Adults sleeping <7h/night show 12% higher all-cause mortality risk (Cappuccio et al., Sleep 2010). Chronic restriction impairs cortisol regulation, immune function, and metabolic health.",
            "days_observed": len(short_nights),
            "data_points": [{"date": s.timestamp.date().isoformat(), "sleep_min": s.sleep_duration_min} for s in short_nights],
        }
    return None


def check_low_hrv(snapshots: list) -> Optional[dict]:
    """HRV significantly below personal 30-day baseline signals autonomic stress."""
    hrv_vals = [(s.timestamp.date().isoformat(), s.hrv_ms) for s in snapshots if s.hrv_ms]
    if len(hrv_vals) < 7:
        return None
    # Use personal baseline (all available data) vs recent 7 days
    baseline_avg = sum(v for _, v in hrv_vals) / len(hrv_vals)
    recent_avg = sum(v for _, v in hrv_vals[-7:]) / 7
    # Flag if recent avg is >20% below personal baseline OR below absolute floor of 20ms
    threshold = min(baseline_avg * 0.80, 20)
    if recent_avg < threshold:
        drop_pct = (baseline_avg - recent_avg) / baseline_avg * 100
        return {
            "pattern_id": "low_hrv",
            "title": "HRV Drop Below Personal Baseline",
            "description": f"7-day average HRV ({recent_avg:.1f}ms) is {drop_pct:.0f}% below your {len(hrv_vals)}-day baseline ({baseline_avg:.1f}ms).",
            "severity": "alert",
            "science_note": "Low HRV reflects reduced parasympathetic activity and is independently associated with cardiovascular disease, all-cause mortality, and poor stress resilience (Bigger et al., Circulation 1992; Thayer et al., Neurosci Biobehav Rev 2012). Personal baseline comparison is more sensitive than population thresholds.",
            "days_observed": 7,
            "data_points": [{"date": d, "hrv_ms": v} for d, v in hrv_vals[-7:]],
        }
    return None


def check_sedentary_streak(snapshots: list) -> Optional[dict]:
    """Prolonged inactivity is associated with metabolic disease independently of exercise."""
    # Only days the tracker was actually worn say anything about activity
    worn = [s for s in snapshots if _was_worn(s)]
    if len(worn) < 5:
        return None
    sedentary_days = [s for s in worn if s.steps is not None and s.steps < 4000]
    if len(sedentary_days) >= 4:
        avg_steps = sum(s.steps for s in sedentary_days) / len(sedentary_days)
        return {
            "pattern_id": "sedentary_streak",
            "title": "Prolonged Sedentary Pattern",
            "description": f"{len(sedentary_days)} of {len(worn)} days with the tracker worn had fewer than 4,000 steps (avg {avg_steps:.0f} steps/day).",
            "severity": "warning",
            "science_note": "Sedentary time >8h/day is associated with a 22% increase in all-cause mortality, independent of exercise habits (Biswas et al., Ann Intern Med 2015). 7,500+ daily steps is the evidence-based minimum for longevity benefit (Saint-Maurice et al., JAMA Int Med 2020).",
            "days_observed": len(sedentary_days),
            "data_points": [{"date": s.timestamp.date().isoformat(), "steps": s.steps} for s in sedentary_days],
        }
    return None


def check_elevated_resting_hr(snapshots: list) -> Optional[dict]:
    """Resting HR >80 bpm is an independent cardiovascular risk marker."""
    hr_vals = [(s.timestamp.date().isoformat(), s.heart_rate_resting) for s in snapshots if s.heart_rate_resting]
    if len(hr_vals) < 5:
        return None
    avg_rhr = sum(v for _, v in hr_vals) / len(hr_vals)
    if avg_rhr > 80:
        return {
            "pattern_id": "elevated_rhr",
            "title": "Elevated Resting Heart Rate",
            "description": f"{len(hr_vals)}-day average resting HR is {avg_rhr:.0f} bpm (above 80 bpm threshold).",
            "severity": "warning",
            "science_note": "Resting HR >80 bpm is associated with 45% higher cardiovascular mortality risk compared to <60 bpm (Jensen et al., Heart 2012). Each 10 bpm increase above 60 confers ~16% higher all-cause mortality.",
            "days_observed": len(hr_vals),
            "data_points": [{"date": d, "rhr": v} for d, v in hr_vals],
        }
    return None


def check_chronic_stress(entries: list) -> Optional[dict]:
    """Self-reported stress >7/10 on multiple days."""
    if len(entries) < 5:
        return None
    high_stress = [e for e in entries if e.numeric_value and e.numeric_value >= 7]
    if len(high_stress) >= 4:
        avg = sum(e.numeric_value for e in high_stress) / len(high_stress)
        return {
            "pattern_id": "chronic_stress",
            "title": "Chronic Elevated Stress",
            "description": f"Self-reported stress ≥7/10 on {len(high_stress)} days (avg {avg:.1f}/10).",
            "severity": "warning",
            "science_note": "Chronic psychological stress accelerates biological aging (telomere shortening), raises cortisol chronically impairing hippocampal neurogenesis, and increases cardiovascular disease risk by up to 40% (Epel et al., PNAS 2004; Kivimäki et al., Lancet 2012).",
            "days_observed": len(high_stress),
            "data_points": [{"date": e.timestamp.date().isoformat(), "stress": e.numeric_value} for e in high_stress],
        }
    return None


def check_low_spo2(snapshots: list) -> Optional[dict]:
    """SpO2 <95% at rest may indicate respiratory or cardiovascular issues."""
    low = [(s.timestamp.date().isoformat(), s.spo2_pct) for s in snapshots if s.spo2_pct and s.spo2_pct < 95]
    if len(low) >= 3:
        avg = sum(v for _, v in low) / len(low)
        return {
            "pattern_id": "low_spo2",
            "title": "Recurring Low Blood Oxygen",
            "description": f"SpO2 below 95% on {len(low)} occasions (avg {avg:.1f}%).",
            "severity": "alert",
            "science_note": "Resting SpO2 <95% is associated with undetected sleep apnea, pulmonary or cardiac conditions. Nocturnal hypoxemia is independently linked to cardiovascular events and cognitive decline (Dempsey et al., Physiol Rev 2010).",
            "days_observed": len(low),
            "data_points": [{"date": d, "spo2": v} for d, v in low],
        }
    return None


def check_pain_trend(entries: list) -> Optional[dict]:
    """Increasing chronic pain may indicate inflammation or degenerative progression."""
    if len(entries) < 5:
        return None
    by_day: dict[str, list[float]] = {}
    for e in entries:
        if e.numeric_value:
            key = e.timestamp.date().isoformat()
            by_day.setdefault(key, []).append(e.numeric_value)
    daily = [(k, sum(v) / len(v)) for k, v in sorted(by_day.items())]
    if len(daily) < 5:
        return None
    # Simple linear trend: compare first half vs second half averages
    mid = len(daily) // 2
    first_avg = sum(v for _, v in daily[:mid]) / mid
    second_avg = sum(v for _, v in daily[mid:]) / (len(daily) - mid)
    if second_avg > first_avg + 1.0:  # Worsening by >1 point
        return {
            "pattern_id": "pain_trend",
            "title": "Worsening Pain Trend",
            "description": f"Average pain increased from {first_avg:.1f} to {second_avg:.1f}/10 over the tracked period.",
            "severity": "alert",
            "science_note": "Progressive pain without resolution may indicate chronic inflammation, structural degradation, or central sensitization. Chronic pain is associated with elevated inflammatory markers (IL-6, CRP) and accelerated aging phenotypes (McBeth et al., Arthritis Rheum 2007).",
            "days_observed": len(daily),
            "data_points": [{"date": d, "pain": v} for d, v in daily],
        }
    return None


def check_mood_decline(entries: list) -> Optional[dict]:
    """Persistent low mood may indicate depression risk."""
    if len(entries) < 5:
        return None
    low_mood = [e for e in entries if e.numeric_value and e.numeric_value <= 4]
    if len(low_mood) >= 4:
        avg = sum(e.numeric_value for e in low_mood) / len(low_mood)
        return {
            "pattern_id": "mood_decline",
            "title": "Persistent Low Mood",
            "description": f"Mood rated ≤4/10 on {len(low_mood)} days (avg {avg:.1f}/10).",
            "severity": "warning",
            "science_note": "Persistent low mood is a key indicator of depressive episodes. Low mood lasting ≥2 weeks warrants clinical evaluation (DSM-5). Associated with elevated inflammatory markers and increased all-cause mortality (Cuijpers et al., J Affect Disord 2014).",
            "days_observed": len(low_mood),
            "data_points": [{"date": e.timestamp.date().isoformat(), "mood": e.numeric_value} for e in low_mood],
        }
    return None


# ── Main entry point ──────────────────────────────────────────────────────────

def get_pattern_alerts(db: Session, user_id: int, days: int = 30) -> list[dict]:
    # Detectors count entries as days, so collapse duplicate syncs first
    snapshots = _one_per_day(_last_n_days_wearable(db, user_id, days))
    stress_entries = _last_n_days_entries(db, user_id, "stress", days)
    pain_entries = _last_n_days_entries(db, user_id, "pain", days)
    mood_entries = _last_n_days_entries(db, user_id, "mood", days)

    alerts = []
    for fn, args in [
        (check_sleep_deficit, [snapshots]),
        (check_low_hrv, [snapshots]),
        (check_sedentary_streak, [snapshots]),
        (check_elevated_resting_hr, [snapshots]),
        (check_low_spo2, [snapshots]),
        (check_chronic_stress, [stress_entries]),
        (check_pain_trend, [pain_entries]),
        (check_mood_decline, [mood_entries]),
    ]:
        result = fn(*args)
        if result:
            alerts.append(result)

    # Sort: alert > warning > info
    severity_order = {"alert": 0, "warning": 1, "info": 2}
    alerts.sort(key=lambda a: severity_order.get(a["severity"], 3))
    return alerts
