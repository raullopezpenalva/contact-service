#!/usr/bin/env python3
"""Capture verified image identity and export a metadata-only publication receipt."""
import argparse
import json
from pathlib import Path
import re
import subprocess


SHA = re.compile(r'[a-f0-9]{40}\Z')
DIGEST = re.compile(r'sha256:[a-f0-9]{64}\Z')
REPOSITORY = re.compile(r'ghcr\.io/raullopezpenalva/(?:personal-website|contact-service)\Z')


def identity(data, repository, revision, user):
    if not REPOSITORY.fullmatch(repository) or not SHA.fullmatch(revision):
        raise ValueError('Invalid source identity')
    source = 'https://github.com/raullopezpenalva/' + (
        'Personal-Website' if repository.endswith('/personal-website') else 'contact-service')
    config = data.get('Config', {})
    labels = config.get('Labels') or {}
    if (data.get('Os') != 'linux' or data.get('Architecture') != 'amd64'
            or config.get('User', '') != user or not DIGEST.fullmatch(data.get('Id', ''))
            or labels.get('org.opencontainers.image.source') != source
            or labels.get('org.opencontainers.image.revision') != revision):
        raise ValueError('Image does not match the verified source/platform/user')
    return {'api_version': 'homelab.verified-image/v1', 'image_repository': repository,
            'source_repository': source, 'source_revision': revision, 'platform': 'linux/amd64',
            'config_digest': data['Id'], 'runtime_user': user}


def publication(verified, push_output):
    digests = set(re.findall(r'\bdigest: (sha256:[a-f0-9]{64})(?:\s|$)', push_output))
    if len(digests) != 1:
        raise ValueError('A single registry digest is required')
    digest = digests.pop()
    return verified | {'api_version': 'homelab.image-publication/v1', 'digest': digest,
                       'image': verified['image_repository'] + '@' + digest,
                       'commit_tag': verified['image_repository'] + ':sha-' + verified['source_revision']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['capture', 'verify', 'receipt'])
    parser.add_argument('--image', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--user', required=True)
    parser.add_argument('--verified', required=True, type=Path)
    parser.add_argument('--push-output', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = subprocess.run(['docker', 'image', 'inspect', args.image],
                                capture_output=True, text=True, check=True, timeout=30)
        actual = identity(json.loads(result.stdout)[0], args.repository, args.revision, args.user)
        if args.operation == 'capture':
            args.verified.write_text(json.dumps(actual, indent=2) + '\n')
        else:
            if json.loads(args.verified.read_text()) != actual:
                raise ValueError('Loaded image differs from the tested artifact')
            if args.operation == 'receipt':
                if not args.push_output or not args.output:
                    raise ValueError('Receipt paths required')
                args.output.write_text(json.dumps(publication(actual, args.push_output.read_text()), indent=2) + '\n')
    except (OSError, ValueError, KeyError, TypeError, IndexError, subprocess.SubprocessError):
        raise SystemExit('Image publication identity rejected; no environment or image configuration printed.') from None
    print('Image identity verified; no deployment performed.')


if __name__ == '__main__':
    main()
