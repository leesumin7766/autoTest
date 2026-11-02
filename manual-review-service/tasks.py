"""Attempt-scoped local process control. Cancel fences late requests and kills OCR/Qwen children."""
import hashlib
import json
import os
import signal
import subprocess
import tempfile
import threading
import time
from dataclasses import dataclass
from pathlib import Path


class Cancelled(Exception):
    pass


class Busy(Exception):
    pass


@dataclass
class Session:
    folder: tempfile.TemporaryDirectory
    source: Path
    digest: str
    total_pages: int
    process: object = None


class TaskRuntime:
    def __init__(self, slot=None):
        self.condition = threading.Condition(threading.RLock())
        self.slot = slot if slot is not None else threading.BoundedSemaphore(1)
        self.sessions = {}
        self.cancelled = {}

    def _check(self, key):
        if key in self.cancelled:
            raise Cancelled()

    def open(self, key, pdf, total_pages):
        with self.condition:
            self._check(key)
            digest = hashlib.sha256(pdf).hexdigest()
            existing = self.sessions.get(key)
            if existing:
                if existing.digest != digest:
                    raise ValueError('INPUT_CHANGED')
                return existing
            folder = tempfile.TemporaryDirectory(prefix='manual-pages-')
            source = Path(folder.name, 'input.pdf')
            source.write_bytes(pdf)
            session = Session(folder, source, digest, total_pages)
            self.sessions[key] = session
            return session

    def session(self, key):
        with self.condition:
            self._check(key)
            return self.sessions[key]

    def progress(self, key):
        with self.condition:
            session = self.sessions.get(key)
            value = {'active': bool(session and session.process), 'cancelled': key in self.cancelled,
                     'totalPages': session.total_pages if session else 0, 'currentPage': 0, 'stage': 'COUNTING'}
            if session:
                try:
                    saved = json.loads(Path(session.folder.name, 'progress.json').read_text(encoding='utf-8'))
                    value.update({k: saved[k] for k in ('currentPage', 'stage') if k in saved})
                except (OSError, ValueError):
                    pass
            return value

    @staticmethod
    def _stop(process):
        if process.poll() is not None:
            return
        try:
            if os.name == 'posix':
                os.killpg(process.pid, signal.SIGTERM)
            else:
                process.terminate()
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            if os.name == 'posix':
                os.killpg(process.pid, signal.SIGKILL)
            else:
                process.kill()
            process.wait(timeout=2)
        except ProcessLookupError:
            pass

    def run(self, key, command, timeout=21600):
        if not self.slot.acquire(blocking=False):
            raise Busy()
        process = None
        try:
            with self.condition:
                self._check(key)
                session = self.sessions[key]
                process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                           start_new_session=os.name == 'posix')
                session.process = process
            try:
                result = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                self._stop(process)
                raise
            with self.condition:
                self._check(key)
            return result
        finally:
            with self.condition:
                session = self.sessions.get(key)
                if session and session.process is process:
                    session.process = None
                self.slot.release()
                self.condition.notify_all()

    def cancel(self, key):
        with self.condition:
            now = time.monotonic()
            self.cancelled = {k: at for k, at in self.cancelled.items() if now - at < 86400}
            self.cancelled[key] = now
            session = self.sessions.get(key)
            process = session.process if session else None
        if process:
            self._stop(process)
        with self.condition:
            return self.condition.wait_for(lambda: not self.sessions.get(key) or not self.sessions[key].process, timeout=5)

    def release(self, key):
        if not self.cancel(key):
            return False
        with self.condition:
            session = self.sessions.pop(key, None)
            if session:
                session.folder.cleanup()
        return True

    def close_all(self):
        for key in list(self.sessions):
            self.release(key)
