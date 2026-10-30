"""Isolated smoke client. Credentials/tokens never enter argv or diagnostic output."""
import json
import os
import sys
import urllib.request
import urllib.error

base, phase = sys.argv[1:]
password = 'Synthetic runtime admin password 47!'
diagnostic = 'initialization'


def call(method, path, expected, body=None, token=None):
    global diagnostic
    diagnostic = method + " " + ("admin operation" if path.startswith("/api/v1/admin/") else path)
    headers = {}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base + path, method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
    try:
        response = urllib.request.urlopen(request, timeout=10)
    except urllib.error.HTTPError as failure:
        response = failure
    if response.status != expected:
        diagnostic += ' expected=' + str(expected) + ' actual=' + str(response.status)
        raise RuntimeError('Unexpected HTTP status')
    if path.startswith('/api/v1/auth/'):
        if response.headers.get('Cache-Control') != 'no-store' or response.headers.get('Pragma') != 'no-cache':
            raise RuntimeError('Missing auth cache prevention')
    raw = response.read()
    return json.loads(raw) if raw else None


def login():
    result = call('POST', '/api/v1/auth/login', 200, {'username': 'smoke-admin', 'password': password})
    if result['expires_in'] != 600 or result['token_type'] != 'Bearer' or 'refresh_token' in result:
        raise RuntimeError('Unexpected token policy')
    return result['access_token']


try:
    if phase == 'before':
        first, second = login(), login()
        call('GET', '/api/v1/auth/me', 200, token=first)
        page = call('GET', '/api/v1/admin/contact/messages', 200, token=first)
        message_id = page['content'][0]['id']
        call('GET', '/api/v1/admin/contact/messages/' + message_id, 200, token=first)
        call('PATCH', '/api/v1/admin/contact/messages/' + message_id + '/status', 200,
             {'status': 'READ', 'adminNote': 'Isolated JWT smoke verification'}, first)
        call('POST', '/api/v1/auth/logout', 204, token=first)
        call('GET', '/api/v1/admin/contact/messages', 401, token=first)
        os.umask(0o077)
        with open('/tmp/auth-state.json', 'w') as state:
            json.dump({'revoked': first, 'active': second}, state)
    elif phase == 'after':
        with open('/tmp/auth-state.json') as state:
            tokens = json.load(state)
        call('GET', '/api/v1/admin/contact/messages', 401, token=tokens['revoked'])
        call('GET', '/api/v1/admin/contact/messages', 200, token=tokens['active'])
        call('POST', '/api/v1/auth/change-password', 204,
             {'currentPassword': password, 'newPassword': 'Replacement runtime password 47!'}, tokens['active'])
        call('GET', '/api/v1/admin/contact/messages', 401, token=tokens['active'])
        call('POST', '/api/v1/auth/login', 401, {'username': 'smoke-admin', 'password': password})
        os.remove('/tmp/auth-state.json')
    else:
        raise RuntimeError('Invalid smoke phase')
except Exception as failure:
    # urllib/parser exceptions may include sensitive values; never print their causes.
    print('Isolated JWT runtime lifecycle verification failed: ' + phase + ': ' + diagnostic + ': ' + type(failure).__name__, file=sys.stderr)
    sys.exit(1)
print('Isolated JWT lifecycle phase passed: ' + phase)
