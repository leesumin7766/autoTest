"""DB-backed claim/lease for preflight evaluations; stores only the verdict, never document text."""
import json
import logging
import time
import uuid
from dataclasses import dataclass
from typing import Any, Callable

# Jev deadline (13s) < wait (15s) < TMS read timeout (20s) < lease (30s) < TMS delivery lease (45s).
LEASE_SECONDS = 30.0
WAIT_SECONDS = 15.0
POLL_SECONDS = 0.5
RETENTION_DAYS = 7
logger = logging.getLogger(__name__)

SCHEMA_SQL = """
    CREATE TABLE IF NOT EXISTS ai_preflight_attempts (
        submission_id UUID NOT NULL,
        payload_hash CHAR(64) NOT NULL,
        status VARCHAR(12) NOT NULL,
        claim_token UUID,
        lease_expires_at TIMESTAMPTZ NOT NULL,
        attempt_count INTEGER NOT NULL DEFAULT 1,
        preflight JSONB,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        PRIMARY KEY (submission_id, payload_hash)
    )
    """


@dataclass(frozen=True)
class Claim:
    kind: str  # OWNER | COMPLETED | BUSY
    token: str | None = None
    preflight: dict[str, Any] | None = None


class PreflightBusy(Exception):
    pass


def initialize(cursor) -> None:
    cursor.execute(SCHEMA_SQL)
    cursor.execute(
        "DELETE FROM ai_preflight_attempts WHERE updated_at < now() - make_interval(days => %s)",
        (RETENTION_DAYS,))


def claim(connect: Callable, submission_id: str, payload_hash: str) -> Claim:
    token = str(uuid.uuid4())
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("""
            INSERT INTO ai_preflight_attempts (submission_id, payload_hash, status, claim_token, lease_expires_at)
            VALUES (%s, %s, 'IN_PROGRESS', %s, now() + make_interval(secs => %s))
            ON CONFLICT (submission_id, payload_hash) DO UPDATE
            SET status = 'IN_PROGRESS', claim_token = EXCLUDED.claim_token,
                lease_expires_at = EXCLUDED.lease_expires_at,
                attempt_count = ai_preflight_attempts.attempt_count + 1, updated_at = now()
            WHERE ai_preflight_attempts.status <> 'COMPLETED' AND ai_preflight_attempts.lease_expires_at <= now()
            RETURNING claim_token
            """, (submission_id, payload_hash, token, LEASE_SECONDS))
        if cursor.fetchone() is not None:
            return Claim("OWNER", token)
        cursor.execute("""
            SELECT status, preflight FROM ai_preflight_attempts
            WHERE submission_id = %s AND payload_hash = %s
            """, (submission_id, payload_hash))
        row = cursor.fetchone()
    if row is not None and row[0] == "COMPLETED":
        stored = row[1]
        return Claim("COMPLETED", preflight=stored if isinstance(stored, dict) else json.loads(stored))
    return Claim("BUSY")


def complete(connect: Callable, submission_id: str, payload_hash: str, token: str, preflight: dict) -> bool:
    """True only if this claim still owned the attempt and its verdict was stored.

    Ownership is lost when another claim replaces the token, not when the lease merely expires.
    """
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("""
            UPDATE ai_preflight_attempts SET status = 'COMPLETED', preflight = %s::jsonb,
                lease_expires_at = now(), updated_at = now()
            WHERE submission_id = %s AND payload_hash = %s AND claim_token = %s AND status = 'IN_PROGRESS'
            """, (json.dumps(preflight, ensure_ascii=False), submission_id, payload_hash, token))
        return cursor.rowcount == 1


def release(connect: Callable, submission_id: str, payload_hash: str, token: str) -> None:
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("""
            UPDATE ai_preflight_attempts SET status = 'FAILED', lease_expires_at = now(), updated_at = now()
            WHERE submission_id = %s AND payload_hash = %s AND claim_token = %s AND status = 'IN_PROGRESS'
            """, (submission_id, payload_hash, token))


def run_once(connect: Callable, submission_id: str, payload_hash: str,
             evaluate: Callable[[], dict], sleep: Callable[[float], None] = time.sleep) -> dict:
    """Return the stored verdict, or evaluate it exactly once across processes."""
    give_up_at = time.monotonic() + WAIT_SECONDS
    while True:
        result = claim(connect, submission_id, payload_hash)
        if result.kind == "COMPLETED":
            return result.preflight
        if result.kind == "OWNER":
            try:
                preflight = evaluate()
            except BaseException:
                release(connect, submission_id, payload_hash, result.token)
                raise
            if complete(connect, submission_id, payload_hash, result.token, preflight):
                return preflight
            # Lease was lost: discard this verdict and use whatever the current owner stores.
            logger.warning("Preflight lease was lost before completion; re-reading the stored verdict")
            if time.monotonic() >= give_up_at:
                raise PreflightBusy()
            continue
        if time.monotonic() >= give_up_at:
            raise PreflightBusy()
        sleep(POLL_SECONDS)
