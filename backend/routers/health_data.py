from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from datetime import datetime, timezone
from typing import Optional
from database import get_db
import models
import schemas
import auth as auth_utils

router = APIRouter(tags=["health data"])


# ── Health Entries ─────────────────────────────────────────────────────────────

@router.post("/entries", response_model=schemas.HealthEntryOut, status_code=201)
def create_entry(
    entry: schemas.HealthEntryCreate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    db_entry = models.HealthEntry(
        user_id=current_user.id,
        timestamp=entry.timestamp or datetime.now(timezone.utc),
        entry_type=entry.entry_type,
        raw_input=entry.raw_input,
        data=entry.data,
        numeric_value=entry.numeric_value,
        sub_category=entry.sub_category,
        source=entry.source or "speech",
    )
    db.add(db_entry)
    db.commit()
    db.refresh(db_entry)
    return db_entry


@router.get("/entries", response_model=list[schemas.HealthEntryOut])
def list_entries(
    entry_type: Optional[str] = None,
    days: int = Query(30, ge=1, le=365),
    limit: int = Query(100, ge=1, le=500),
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    from datetime import timedelta
    since = datetime.now(timezone.utc) - timedelta(days=days)
    q = db.query(models.HealthEntry).filter(
        models.HealthEntry.user_id == current_user.id,
        models.HealthEntry.timestamp >= since,
    )
    if entry_type:
        q = q.filter(models.HealthEntry.entry_type == entry_type)
    return q.order_by(models.HealthEntry.timestamp.desc()).limit(limit).all()


@router.get("/entries/{entry_id}", response_model=schemas.HealthEntryOut)
def get_entry(
    entry_id: int,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    entry = db.query(models.HealthEntry).filter(
        models.HealthEntry.id == entry_id,
        models.HealthEntry.user_id == current_user.id,
    ).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")
    return entry


@router.patch("/entries/{entry_id}", response_model=schemas.HealthEntryOut)
def update_entry(
    entry_id: int,
    patch: schemas.HealthEntryPatch,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    entry = db.query(models.HealthEntry).filter(
        models.HealthEntry.id == entry_id,
        models.HealthEntry.user_id == current_user.id,
    ).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")
    for field, value in patch.model_dump(exclude_unset=True).items():
        setattr(entry, field, value)
    db.commit()
    db.refresh(entry)
    return entry


@router.delete("/entries/{entry_id}", status_code=204)
def delete_entry(
    entry_id: int,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    entry = db.query(models.HealthEntry).filter(
        models.HealthEntry.id == entry_id,
        models.HealthEntry.user_id == current_user.id,
    ).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")
    db.delete(entry)
    db.commit()


# ── Wearable Snapshots ─────────────────────────────────────────────────────────

@router.post("/wearable", response_model=schemas.WearableSnapshotOut, status_code=201)
def create_wearable_snapshot(
    snapshot: schemas.WearableSnapshotCreate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    db_snap = models.WearableSnapshot(user_id=current_user.id, **snapshot.model_dump())
    db.add(db_snap)
    db.commit()
    db.refresh(db_snap)
    return db_snap


@router.post("/wearable/batch", status_code=201)
def create_wearable_snapshots_batch(
    snapshots: list[schemas.WearableSnapshotCreate],
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    """Bulk upsert for daily sync from Android — ignores duplicates on (user_id, timestamp, device_name)."""
    inserted = 0
    for s in snapshots:
        stmt = (
            sqlite_insert(models.WearableSnapshot)
            .values(user_id=current_user.id, **s.model_dump())
            .on_conflict_do_nothing(index_elements=["user_id", "timestamp", "device_name"])
        )
        result = db.execute(stmt)
        inserted += result.rowcount
    db.commit()
    return {"inserted": inserted}


@router.get("/wearable", response_model=list[schemas.WearableSnapshotOut])
def list_wearable_snapshots(
    days: int = Query(30, ge=1, le=365),
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth_utils.get_current_user),
):
    from datetime import timedelta
    since = datetime.now(timezone.utc) - timedelta(days=days)
    return (
        db.query(models.WearableSnapshot)
        .filter(
            models.WearableSnapshot.user_id == current_user.id,
            models.WearableSnapshot.timestamp >= since,
        )
        .order_by(models.WearableSnapshot.timestamp.desc())
        .all()
    )
