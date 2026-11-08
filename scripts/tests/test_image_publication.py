"""Image handoff rejects mismatched identity and ambiguous registry results."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('publication', Path(__file__).resolve().parents[1] / 'image-publication.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PublicationIdentity(unittest.TestCase):
    def setUp(self):
        self.repository = 'ghcr.io/raullopezpenalva/contact-service'
        self.revision = 'a' * 40
        self.image = {'Id': 'sha256:' + 'b' * 64, 'Architecture': 'amd64', 'Os': 'linux',
                      'Config': {'User': '10001:10001', 'Env': ['SECRET=not-for-receipts'], 'Labels': {
                          'org.opencontainers.image.source': 'https://github.com/raullopezpenalva/contact-service',
                          'org.opencontainers.image.revision': self.revision}}}

    def selected(self, image=None):
        return MODULE.identity(image or self.image, self.repository, self.revision, '10001:10001')

    def test_metadata_excludes_image_environment(self):
        metadata = self.selected()
        self.assertNotIn('SECRET', json.dumps(metadata))
        self.assertEqual(metadata['config_digest'], self.image['Id'])

    def test_missing_wrong_repository_or_revision_is_rejected(self):
        for labels in [{}, {'org.opencontainers.image.source': 'https://example.invalid',
                            'org.opencontainers.image.revision': self.revision},
                       {'org.opencontainers.image.source': 'https://github.com/raullopezpenalva/contact-service',
                        'org.opencontainers.image.revision': 'c' * 40}]:
            with self.subTest(labels=labels), self.assertRaises(ValueError):
                self.selected(self.image | {'Config': self.image['Config'] | {'Labels': labels}})

    def test_wrong_architecture_os_user_or_config_digest_is_rejected(self):
        for change in [{'Architecture': 'arm64'}, {'Os': 'windows'}, {'Id': 'mutable-tag'},
                       {'Config': self.image['Config'] | {'User': '0'}}]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.selected(self.image | change)

    def test_unapproved_repository_and_invalid_source_commit_are_rejected(self):
        for repository, revision in [('ghcr.io/other/contact-service', self.revision),
                                     (self.repository, 'main')]:
            with self.assertRaises(ValueError):
                MODULE.identity(self.image, repository, revision, '10001:10001')

    def test_registry_receipt_uses_digest_not_a_moving_tag(self):
        digest = 'sha256:' + 'c' * 64
        result = MODULE.publication(self.selected(), 'sha-tag: digest: ' + digest + ' size: 1234\n')
        self.assertEqual(result['image'], self.repository + '@' + digest)
        self.assertEqual(result['source_revision'], self.revision)

    def test_absent_or_ambiguous_push_digest_is_rejected(self):
        for output in ['', 'denied: requested access is denied',
                       'digest: sha256:' + 'c' * 64 + '\ndigest: sha256:' + 'd' * 64]:
            with self.subTest(output=output), self.assertRaises(ValueError):
                MODULE.publication(self.selected(), output)

    def test_handoff_rejects_rebuilt_image_with_other_config_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            metadata = Path(directory) / 'verified.json'
            metadata.write_text(json.dumps(self.selected() | {'config_digest': 'sha256:' + 'e' * 64}))
            argv = ['image-publication.py', 'verify', '--image', 'local:tested',
                    '--repository', self.repository, '--revision', self.revision,
                    '--user', '10001:10001', '--verified', str(metadata)]
            result = subprocess.CompletedProcess([], 0, json.dumps([self.image]), '')
            with patch.object(sys, 'argv', argv), patch.object(MODULE.subprocess, 'run', return_value=result):
                with self.assertRaises(SystemExit):
                    MODULE.main()


if __name__ == '__main__':
    unittest.main()
