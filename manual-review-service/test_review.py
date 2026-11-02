import unittest
from datetime import date
from review import lifecycle_candidates, typo_candidates, review_blocks, chunks, load_catalog, ReviewFailure


class ReviewTest(unittest.TestCase):
    def block(self, text):
        return {'page': 3, 'blockId': '3:0', 'text': text, 'bbox': [10, 20, 300, 100]}

    def test_eol_never_confirms_defect_and_keeps_official_reference(self):
        found = lifecycle_candidates(self.block('지원 OS: Windows 10 Pro'), date(2026, 11, 2))
        self.assertEqual(1, len(found))
        self.assertEqual('NEEDS_REVIEW', found[0]['status'])
        self.assertEqual('2025-10-14', found[0]['reference']['supportEnd'])
        self.assertTrue(found[0]['reference']['url'].startswith('https://learn.microsoft.com/'))
        self.assertEqual(3, found[0]['page'])

    def test_future_supported_and_explicitly_unsupported_are_not_eol_defects(self):
        self.assertEqual([], lifecycle_candidates(self.block('Windows 10 Pro 지원'), date(2024, 1, 1)))
        self.assertEqual([], lifecycle_candidates(self.block('Windows 10은 지원하지 않습니다.'), date(2026, 11, 2)))
        self.assertEqual([], lifecycle_candidates(self.block('Windows 11 지원'), date(2026, 11, 2)))
        self.assertEqual([], lifecycle_candidates(self.block('Windows 100 지원'), date(2026, 11, 2)))

    def test_ltsc_esu_and_unspecified_edition_require_scope_check(self):
        for text in ['Windows 10 LTSC 지원', 'Windows 10 ESU 지원', 'Windows 10 지원']:
            found = lifecycle_candidates(self.block(text), date(2026, 11, 2))
            self.assertEqual('CHECK_EDITION', found[0]['reasonCode'])

    def test_llm_invented_quote_and_broad_rewrites_are_rejected(self):
        block = self.block('설정이 저장됬습니다.')
        supplied = [
            {'quote': '존재하지 않는 문장', 'wrong': '문장', 'correction': '문구'},
            {'quote': block['text'], 'wrong': '저장됬습니다', 'correction': '저장되었습니다'},
            {'quote': block['text'], 'wrong': '저장됬습니다', 'correction': '저장됬습니다'},
        ]
        found = typo_candidates(block, supplied)
        self.assertEqual(1, len(found))
        self.assertEqual('저장되었습니다', found[0]['suggestion'])
        self.assertEqual(block['text'].index('저장됬습니다'), found[0]['characterStart'])

    def test_every_character_is_covered_and_error_is_partial_not_clean(self):
        text = '가' * 6000
        parts = list(chunks(text))
        self.assertGreater(len(parts), 1)
        self.assertEqual(text, ''.join(text[start:end] for start, end in parts))
        def broken(_):
            raise TimeoutError()
        result = review_blocks([self.block('Windows 10 Pro 지원')], broken, date(2026, 11, 2))
        self.assertEqual('PARTIAL', result['status'])
        self.assertEqual(1, result['failedChunks'])
        self.assertEqual(1, len(result['candidates']))

    def test_catalog_has_version_and_verified_dates(self):
        catalog = load_catalog()
        self.assertTrue(catalog['version'])
        self.assertTrue(all(item['checkedAt'] and item['url'] for item in catalog['products']))

    def test_truncated_model_output_retries_smaller_complete_ranges(self):
        def suggest(text):
            if len(text) > 600:
                raise ReviewFailure('LLM_OUTPUT_TRUNCATED')
            return []
        result = review_blocks([self.block('가' * 1000)], suggest, date(2026, 11, 2))
        self.assertEqual('COMPLETED', result['status'])
        self.assertEqual(0, result['failedChunks'])
        self.assertEqual(2, result['reviewedChunks'])
        self.assertEqual(1, result['retriedChunks'])


if __name__ == '__main__':
    unittest.main()
