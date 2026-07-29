from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from database import get_db
import models
import schemas
import auth as auth_utils
from services.correlation import compute_correlation, METRIC_SOURCES
from services.pattern_analysis import get_pattern_alerts

router = APIRouter(prefix="/analysis", tags=["analysis"])


@router.get("/correlations")
def correlations(
    metric1: str = Query(..., description=f"One of: {', '.join(METRIC_SOURCES.keys())}"),
    metric2: str = Query(...),
    days: int = Query(30, ge=7, le=365),
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    return compute_correlation(db, current_user.id, metric1, metric2, days)


@router.get("/correlations/matrix")
def correlation_matrix(
    days: int = Query(30, ge=7, le=365),
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    """Compute all pairwise correlations for available metrics."""
    metrics = list(METRIC_SOURCES.keys())
    results = []
    for i, m1 in enumerate(metrics):
        for m2 in metrics[i + 1:]:
            result = compute_correlation(db, current_user.id, m1, m2, days)
            if result["n_samples"] >= 5:
                results.append(result)
    results.sort(key=lambda r: abs(r["pearson_r"]), reverse=True)
    return results


@router.get("/patterns")
def patterns(
    days: int = Query(30, ge=7, le=90),
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    return get_pattern_alerts(db, current_user.id, days)


@router.get("/summary")
def summary(
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    """Today's summary of the most recent wearable snapshot + recent entries."""
    from datetime import timedelta, timezone
    from datetime import datetime

    # Latest wearable snapshot
    latest_snap = (
        db.query(models.WearableSnapshot)
        .filter(models.WearableSnapshot.user_id == current_user.id)
        .order_by(models.WearableSnapshot.timestamp.desc())
        .first()
    )

    # Entries from today
    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    today_entries = (
        db.query(models.HealthEntry)
        .filter(
            models.HealthEntry.user_id == current_user.id,
            models.HealthEntry.timestamp >= today_start,
        )
        .order_by(models.HealthEntry.timestamp.desc())
        .all()
    )

    return {
        "latest_wearable": latest_snap.to_dict() if latest_snap else None,
        "today_entries": [e.to_dict() for e in today_entries],
        "pattern_alert_count": len(get_pattern_alerts(db, current_user.id, 14)),
    }
