import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import tempfile
import time
import unittest

IME = Path(__file__).resolve().parents[1] / 'linuxfs/overlay/usr/local/bin/droiddeck-ime'


def message(obj, opcode, payload=b''):
    return struct.pack('<II', obj, ((8 + len(payload)) << 16) | opcode) + payload


def wl_string(value):
    data = value.encode() + b'\0'
    return struct.pack('<I', len(data)) + data + b'\0' * (-len(data) % 4)


class DesktopImeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.session = Path(self.temp.name)
        self.server, client = socket.socketpair(socket.AF_UNIX, socket.SOCK_STREAM)
        self.addCleanup(self.server.close)
        env = dict(os.environ, BL_LAUNCH_DIR=str(self.session), WAYLAND_SOCKET=str(client.fileno()))
        self.proc = subprocess.Popen(['python3', str(IME)], env=env, pass_fds=[client.fileno()],
                                     stderr=subprocess.PIPE, text=True)
        client.close()
        self.addCleanup(self.stop)
        self.server.settimeout(5)
        self.assertEqual(self.read(12), message(1, 1, struct.pack('<I', 2)))
        self.server.sendall(message(2, 0, struct.pack('<I', 7) + wl_string('zwp_input_method_v1') + struct.pack('<I', 1)))
        bind = self.read(8 + 4 + len(wl_string('zwp_input_method_v1')) + 8)
        self.assertEqual(bind, message(2, 0, struct.pack('<I', 7) + wl_string('zwp_input_method_v1') + struct.pack('<II', 1, 3)))

    def stop(self):
        if self.proc.poll() is None:
            self.proc.kill()
        self.proc.wait(5)
        self.proc.stderr.close()

    def read(self, size):
        data = b''
        while len(data) < size:
            chunk = self.server.recv(size - len(data))
            if not chunk:
                break
            data += chunk
        return data

    def state(self, seq, timeout=5):
        path = self.session / 'agent/ime.json'
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                snapshot = json.loads(path.read_text())
            except (OSError, ValueError):
                snapshot = None
            if snapshot and snapshot['seq'] >= seq:
                return snapshot
            time.sleep(0.02)
        self.fail('ime.json never reached seq %d' % seq)

    def test_focus_and_blur_are_reported(self):
        self.server.sendall(message(3, 0, struct.pack('<I', 100)))
        self.assertTrue(self.state(1)['textField'])
        self.server.sendall(message(100, 2, struct.pack('<II', 0, 9)))
        snapshot = self.state(2)
        self.assertEqual((snapshot['textField'], snapshot['purpose']), (True, 9))
        self.server.sendall(message(3, 1, struct.pack('<I', 100)))
        self.assertEqual(self.read(8), message(100, 0))
        self.assertFalse(self.state(3)['textField'])
        self.assertEqual(oct((self.session / 'agent/ime.json').stat().st_mode & 0o777), '0o600')

    def test_switching_fields_does_not_flicker(self):
        self.server.sendall(message(3, 0, struct.pack('<I', 100)))
        self.assertTrue(self.state(1)['textField'])
        self.server.sendall(message(3, 1, struct.pack('<I', 100)) + message(3, 0, struct.pack('<I', 101)))
        self.assertEqual(self.read(8), message(100, 0))
        time.sleep(0.4)
        self.assertEqual(self.state(1), {'textField': True, 'hint': 0, 'purpose': 0, 'seq': 1})

    def test_compositor_exit_clears_the_field(self):
        self.server.sendall(message(3, 0, struct.pack('<I', 100)))
        self.assertTrue(self.state(1)['textField'])
        self.server.close()
        self.assertEqual(self.proc.wait(5), 0)
        self.assertFalse(self.state(2)['textField'])


    def test_a_protocol_error_clears_the_field(self):
        self.server.sendall(message(3, 0, struct.pack('<I', 100)))
        self.assertTrue(self.state(1)['textField'])
        self.server.sendall(message(1, 0, struct.pack('<II', 3, 1) + wl_string('bad request')))
        self.assertEqual(self.proc.wait(5), 1)
        self.assertFalse(self.state(2)['textField'])

    def send_host(self, *commands):
        fifo = self.session / 'agent/ime-input'
        deadline = time.monotonic() + 5
        while not fifo.exists() and time.monotonic() < deadline:
            time.sleep(0.02)
        fd = os.open(fifo, os.O_WRONLY | os.O_NONBLOCK)
        try:
            for command in commands:
                os.write(fd, (json.dumps(command, ensure_ascii=False) + '\n').encode())
        finally:
            os.close(fd)

    def focus(self, context=100, serial=7):
        self.server.sendall(message(3, 0, struct.pack('<I', context)))
        self.assertTrue(self.state(1)['textField'])
        self.server.sendall(message(context, 4, struct.pack('<I', serial)))

    def test_android_text_is_committed_to_the_focused_field(self):
        self.focus()
        self.send_host({'op': 'commit', 'text': '繁體中文'})
        expected = message(100, 1, struct.pack('<I', 7) + wl_string('繁體中文'))
        self.assertEqual(self.read(len(expected)), expected)

    def test_composition_is_sent_as_preedit_with_a_byte_cursor(self):
        self.focus()
        self.send_host({'op': 'preedit', 'text': 'ㄓㄨㄥ', 'cursor': 2})
        cursor = message(100, 4, struct.pack('<i', len('ㄓㄨ'.encode())))
        preedit = message(100, 2, struct.pack('<I', 7) + wl_string('ㄓㄨㄥ') + wl_string(''))
        self.assertEqual(self.read(len(cursor) + len(preedit)), cursor + preedit)

    def test_long_text_is_split_between_characters(self):
        self.focus()
        text = '測' * 1500
        self.send_host({'op': 'commit', 'text': text})
        first = message(100, 1, struct.pack('<I', 7) + wl_string('測' * 1000))
        second = message(100, 1, struct.pack('<I', 7) + wl_string('測' * 500))
        self.assertEqual(self.read(len(first) + len(second)), first + second)

    def test_text_without_a_focused_field_is_dropped(self):
        self.send_host({'op': 'commit', 'text': 'lost'})
        self.focus()
        self.send_host({'op': 'commit', 'text': 'ok'})
        expected = message(100, 1, struct.pack('<I', 7) + wl_string('ok'))
        self.assertEqual(self.read(len(expected)), expected)

    def test_malformed_lines_do_not_stop_the_channel(self):
        self.focus()
        fifo = self.session / 'agent/ime-input'
        fd = os.open(fifo, os.O_WRONLY | os.O_NONBLOCK)
        os.write(fd, b'not json\n\xff\xfe\n')
        os.close(fd)
        self.send_host({'op': 'commit', 'text': 'a'})
        expected = message(100, 1, struct.pack('<I', 7) + wl_string('a'))
        self.assertEqual(self.read(len(expected)), expected)


class DesktopScaleTest(unittest.TestCase):
    def load(self):
        import importlib.machinery
        import importlib.util
        loader = importlib.machinery.SourceFileLoader('droiddeck_ime', str(IME))
        spec = importlib.util.spec_from_loader('droiddeck_ime', loader)
        module = importlib.util.module_from_spec(spec)
        loader.exec_module(module)
        return module

    def test_only_offered_scales_are_accepted(self):
        ime = self.load()
        self.assertEqual(ime.parse_scale(1.25), 1.25)
        self.assertEqual(ime.parse_scale('1.75'), 1.75)
        self.assertIsNone(ime.parse_scale(1.3))
        self.assertIsNone(ime.parse_scale('x'))

    def test_scale_reaches_every_enabled_output_on_the_nested_socket(self):
        ime = self.load()
        calls = []

        class Result:
            def __init__(self, stdout=b'', returncode=0):
                self.stdout, self.returncode = stdout, returncode

        def run(args, **kwargs):
            calls.append((args, kwargs['env'].get('WAYLAND_DISPLAY'), 'WAYLAND_SOCKET' in kwargs['env']))
            if args == ['kscreen-doctor', '-j']:
                return Result(json.dumps({'outputs': [
                    {'name': 'WL-1', 'enabled': True}, {'name': 'WL-2', 'enabled': False}]}).encode())
            return Result()

        old = dict(os.environ)
        os.environ.update(BL_DESKTOP='1', WAYLAND_SOCKET='5')
        try:
            self.assertTrue(ime.apply_scale(1.5, run))
        finally:
            os.environ.clear()
            os.environ.update(old)
        self.assertEqual(calls[1][0], ['kscreen-doctor', 'output.WL-1.scale.1.5'])
        self.assertEqual({(c[1], c[2]) for c in calls}, {('wayland-1', False)})

    def test_chunks_respect_the_request_limit(self):
        ime = self.load()
        chunks = list(ime.text_chunks('a測😀' * 800))
        self.assertEqual(''.join(chunks), 'a測😀' * 800)
        self.assertTrue(all(len(c.encode()) <= ime.TEXT_CHUNK for c in chunks))


if __name__ == '__main__':
    unittest.main()
