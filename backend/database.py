from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from models import Base
import os

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./health_tracker.db")

# Several hosts still hand out the legacy postgres:// scheme, which SQLAlchemy 2 rejects
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)

is_sqlite = DATABASE_URL.startswith("sqlite")

# SQLite-specific connect args
connect_args = {"check_same_thread": False} if is_sqlite else {}

# Serverless Postgres (Neon, Supabase) suspends idle compute and drops pooled
# connections, so verify one before handing it out rather than failing a request.
pool_args = {} if is_sqlite else {"pool_pre_ping": True, "pool_recycle": 300}

engine = create_engine(DATABASE_URL, connect_args=connect_args, **pool_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db():
    Base.metadata.create_all(bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
