import base64
import json
import os
import unittest
from uuid import uuid4
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
import app


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'PRODUCT_DESCRIPTION_INTERNAL_TOKEN': 'test-token'})
        self.env.start()
        self.client = TestClient(app.app)
        self.headers = {'X-Internal-Token': 'test-token'}
        self.body = {'pdfBase64': base64.b64encode(b'%PDF-fixture').decode(), 'asOf': '2026-11-02'}
        self.task = f'/tasks/{uuid4()}/1'

    def tearDown(self):
        if hasattr(app, 'runtime'):
            app.runtime.close_all()
        self.env.stop()

    def test_internal_auth_and_invalid_input_never_launch_ocr(self):
        with patch('app.subprocess.run') as process:
            self.assertEqual(403, self.client.post('/analyze', json=self.body).status_code)
            self.assertEqual(422, self.client.post('/analyze', headers=self.headers, json=dict(self.body, pdfBase64='not pdf')).status_code)
            invalid = self.client.post('/analyze', headers=self.headers, json=dict(self.body, asOf='private-document-marker'))
            self.assertEqual(422, invalid.status_code)
            self.assertNotIn('private-document-marker', invalid.text)
            process.assert_not_called()

    def test_another_job_cannot_run_and_failed_ocr_does_not_report_clean(self):
        app.slot.acquire()
        try:
            self.assertEqual(409, self.client.post('/analyze', headers=self.headers, json=self.body).status_code)
        finally:
            app.slot.release()
        with patch('app.subprocess.run', side_effect=OSError('private-document-marker')):
            response = self.client.post('/analyze', headers=self.headers, json=self.body)
            self.assertEqual(503, response.status_code)
            self.assertNotIn('private-document-marker', response.text)

    def test_unreadable_page_means_partial_even_when_qwen_returns_no_candidates(self):
        def extracted(command, **kwargs):
            self.assertEqual(kwargs['stdout'], app.subprocess.DEVNULL)
            Path(command[-1]).write_text(json.dumps({'blocks': [{'page': 1, 'blockId': '1:0', 'text': 'Windows 10 Pro 지원'}], 'pageCount': 2, 'emptyPages': [2]}), encoding='utf-8')
        with patch('app.subprocess.run', side_effect=extracted), patch('app.qwen', return_value=[]):
            response = self.client.post('/analyze', headers=self.headers, json=self.body)
            self.assertEqual(200, response.status_code)
            self.assertEqual('PARTIAL', response.json()['status'])
            self.assertEqual('NEEDS_REVIEW', response.json()['candidates'][0]['status'])
            self.assertTrue(response.json()['warnings'])

    def test_external_model_url_is_rejected_before_network_call(self):
        with patch.dict(os.environ, {'OLLAMA_BASE_URL': 'https://external.invalid'}), patch('app.httpx.Client') as client:
            with self.assertRaises(ValueError):
                app.qwen('문서 데이터')
            client.assert_not_called()

    def test_qwen_uses_schema_and_truncated_output_is_not_accepted(self):
        with patch('app.httpx.Client') as constructor:
            provider = constructor.return_value.__enter__.return_value
            response = provider.post.return_value
            response.json.return_value = {'done_reason': 'stop', 'message': {'content': '{"candidates":[]}'}}
            self.assertEqual([], app.qwen('검사할 원문'))
            self.assertEqual('object', provider.post.call_args.kwargs['json']['format']['type'])
            self.assertFalse(constructor.call_args.kwargs['trust_env'])
            response.json.return_value = {'done_reason': 'length', 'message': {'content': '{"candidates":['}}
            with self.assertRaises(app.ReviewFailure) as error:
                app.qwen('검사할 원문')
            self.assertEqual('LLM_OUTPUT_TRUNCATED', error.exception.code)

    def test_total_page_count_is_returned_before_ocr_and_cancel_fences_late_requests(self):
        with patch('app.pdf_information', return_value=81), patch('app.subprocess.Popen') as process:
            response = self.client.post(self.task + '/prepare', headers=self.headers, json=self.body)
            self.assertEqual(200, response.status_code)
            self.assertEqual(81, response.json()['totalPages'])
            self.assertTrue(response.json()['processorVersion'])
            process.assert_not_called()
            self.assertEqual(403, self.client.post(self.task + '/cancel').status_code)
            cancelled = self.client.post(self.task + '/cancel', headers=self.headers)
            self.assertTrue(cancelled.json()['stopped'])
            late = self.client.post(self.task + '/prepare', headers=self.headers, json=self.body)
            self.assertEqual(409, late.status_code)
            self.assertEqual(409, self.client.post(self.task + '/pages/1', headers=self.headers, json={'asOf': '2026-11-02'}).status_code)
            next_attempt = self.task[:-1] + '2'
            self.assertEqual(200, self.client.post(next_attempt + '/prepare', headers=self.headers, json=self.body).status_code)


if __name__ == '__main__':
    unittest.main()
