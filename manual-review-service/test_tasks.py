import json
import os
import subprocess
import tempfile
import sys
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch
from tasks import TaskRuntime, Cancelled, Busy


class TaskTest(unittest.TestCase):
    def setUp(self):
        self.runtime = TaskRuntime()

    def tearDown(self):
        self.runtime.close_all()

    def test_cancel_before_registration_fences_late_request(self):
        self.assertTrue(self.runtime.cancel('job:1'))
        with self.assertRaises(Cancelled):
            self.runtime.open('job:1', b'pdf', 81)

    def test_count_and_live_phase_are_available_before_page_finishes(self):
        session = self.runtime.open('job:1', b'pdf', 81)
        Path(session.folder.name, 'progress.json').write_text(json.dumps({'stage': 'REVIEWING', 'currentPage': 3}), encoding='utf-8')
        self.assertEqual(81, self.runtime.progress('job:1')['totalPages'])
        self.assertEqual('REVIEWING', self.runtime.progress('job:1')['stage'])
        self.assertEqual(3, self.runtime.progress('job:1')['currentPage'])

    def test_running_process_is_stopped_and_old_attempt_cannot_stop_resume(self):
        self.runtime.open('job:1', b'pdf', 2)
        stopped = threading.Event()
        def run():
            try:
                self.runtime.run('job:1', [sys.executable, '-c', 'import time; time.sleep(30)'])
            except Cancelled:
                stopped.set()
        thread = threading.Thread(target=run)
        thread.start()
        deadline = time.monotonic() + 5
        while not self.runtime.progress('job:1')['active'] and time.monotonic() < deadline:
            time.sleep(0.01)
        self.runtime.open('job:2', b'pdf', 2)
        with self.assertRaises(Busy):
            self.runtime.run('job:2', [sys.executable, '-c', 'pass'])
        started = time.monotonic()
        self.assertTrue(self.runtime.cancel('job:1'))
        thread.join(timeout=3)
        self.assertTrue(stopped.is_set())
        self.assertLess(time.monotonic() - started, 5)
        self.assertTrue(self.runtime.cancel('job:1'))
        self.assertEqual(0, self.runtime.run('job:2', [sys.executable, '-c', 'pass']))

    def test_releasing_session_removes_input_and_retains_cancel_fence(self):
        session = self.runtime.open('job:1', b'pdf', 1)
        source = session.source
        self.assertTrue(source.exists())
        self.assertTrue(self.runtime.release('job:1'))
        self.assertFalse(source.exists())
        with self.assertRaises(Cancelled):
            self.runtime.open('job:1', b'pdf', 1)

    def test_timeout_stops_process_and_releases_cpu_slot(self):
        self.runtime.open('job:1', b'pdf', 1)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.runtime.run('job:1', [sys.executable, '-c', 'import time;time.sleep(30)'], timeout=0.05)
        self.assertFalse(self.runtime.progress('job:1')['active'])
        self.runtime.open('job:2', b'pdf', 1)
        self.assertEqual(0, self.runtime.run('job:2', [sys.executable, '-c', 'pass']))

    @unittest.skipUnless(os.name == 'posix', 'Process-group check runs in the Linux CPU image')
    def test_stop_also_terminates_nested_ocr_child(self):
        self.runtime.open('job:1', b'pdf', 1)
        with tempfile.TemporaryDirectory() as folder:
            pidfile = Path(folder, 'child.pid')
            code = 'import subprocess,sys,time,pathlib; p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(30)"]); pathlib.Path(sys.argv[1]).write_text(str(p.pid)); time.sleep(30)'
            def run():
                try:
                    self.runtime.run('job:1', [sys.executable, '-c', code, str(pidfile)])
                except Cancelled:
                    pass
            thread = threading.Thread(target=run); thread.start()
            deadline = time.monotonic() + 5
            while not pidfile.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(pidfile.exists())
            pid = int(pidfile.read_text())
            self.assertTrue(self.runtime.cancel('job:1')); thread.join(timeout=3)
            status = Path(f'/proc/{pid}/stat')
            deadline = time.monotonic() + 2
            while status.exists() and status.read_text().split()[2] not in ('Z', 'X') and time.monotonic() < deadline:
                time.sleep(0.01)
            if status.exists():
                self.assertIn(status.read_text().split()[2], ('Z', 'X'))


if __name__ == '__main__':
    unittest.main()
