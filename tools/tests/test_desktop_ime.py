import json
import os
from pathlib import Path
import select
import socket
import stat
import struct
import subprocess
import tempfile
import time
import unittest

IME = Path(__file__).resolve().parents[1] / 'linuxfs/overlay/usr/local/bin/droiddeck-ime'


def message(obj, opcode, payload=b''):
    return struct.pack('<II', obj, ((8 + len(payload)) << 16) | opcode) + payload


def read_string(payload, offset):
    (length,) = struct.unpack_from('<I', payload, offset)
    start = offset + 4
    return payload[start:start + length - 1].decode(), start + length + (-length % 4)


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

    def message_from_server(self):
        header = self.read(8)
        self.assertEqual(len(header), 8)
        obj, word = struct.unpack('<II', header)
        return obj, word & 0xFFFF, self.read((word >> 16) - 8)

    def fifo(self):
        path = self.session / 'agent/ime-input'
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            try:
                if stat.S_ISFIFO(path.stat().st_mode):
                    return path
            except OSError:
                pass
            time.sleep(0.02)
        self.fail('ime-input was never created')

    def type(self, *requests):
        fd = os.open(self.fifo(), os.O_WRONLY | os.O_NONBLOCK)
        try:
            for request in requests:
                os.write(fd, (request if isinstance(request, bytes) else json.dumps(request).encode()) + b'\n')
        finally:
            os.close(fd)

    def focus(self, context=100, serial=7):
        self.server.sendall(message(3, 0, struct.pack('<I', context)))
        self.assertTrue(self.state(1)['textField'])
        self.server.sendall(message(context, 4, struct.pack('<I', serial)))

    def test_the_text_channel_is_a_private_fifo(self):
        path = self.fifo()
        self.assertEqual(oct(path.stat().st_mode & 0o777), '0o600')

    def test_committed_text_reaches_the_focused_field(self):
        self.focus()
        self.type({'op': 'commit', 'text': '中文 abc'})
        self.assertEqual(self.message_from_server(), (100, 1, struct.pack('<I', 7) + wl_string('中文 abc')))

    def test_composition_is_shown_then_replaced_by_the_commit(self):
        self.focus()
        self.type({'op': 'preedit', 'text': 'ㄓㄨ', 'cursor': 1})
        self.assertEqual(self.message_from_server(), (100, 4, struct.pack('<i', len('ㄓ'.encode()))))
        self.assertEqual(self.message_from_server(), (100, 2, struct.pack('<I', 7) + wl_string('ㄓㄨ') + wl_string('')))
        self.type({'op': 'commit', 'text': '注'})
        self.assertEqual(self.message_from_server(), (100, 4, struct.pack('<i', 0)))
        self.assertEqual(self.message_from_server(), (100, 2, struct.pack('<I', 7) + wl_string('') + wl_string('')))
        self.assertEqual(self.message_from_server(), (100, 1, struct.pack('<I', 7) + wl_string('注')))

    def test_an_empty_composition_without_one_showing_sends_nothing(self):
        self.focus()
        self.type({'op': 'preedit', 'text': ''}, {'op': 'commit', 'text': 'a'})
        self.assertEqual(self.message_from_server(), (100, 1, struct.pack('<I', 7) + wl_string('a')))

    def test_long_text_is_split_under_the_message_limit_on_character_boundaries(self):
        self.focus()
        text = '漢' * 2500
        self.type({'op': 'commit', 'text': text})
        received = ''
        while len(received) < len(text):
            obj, opcode, payload = self.message_from_server()
            self.assertEqual((obj, opcode), (100, 1))
            self.assertLessEqual(len(payload) + 8, 4096)
            chunk, _ = read_string(payload, 4)
            received += chunk
        self.assertEqual(received, text)

    def log_line(self, needle, timeout=5):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            ready, _, _ = select.select([self.proc.stderr], [], [], max(0.0, deadline - time.monotonic()))
            if ready and needle in self.proc.stderr.readline():
                return
        self.fail('droiddeck-ime never logged %r' % needle)

    def test_text_without_a_focused_field_is_dropped(self):
        self.type({'op': 'commit', 'text': 'lost'})
        self.log_line('dropped')
        self.focus()
        self.type({'op': 'commit', 'text': 'kept'})
        self.assertEqual(self.message_from_server(), (100, 1, struct.pack('<I', 7) + wl_string('kept')))

    def test_malformed_requests_are_skipped(self):
        self.focus()
        self.type(b'not json', b'[1, 2]', {'op': 'commit', 'text': 5}, {'op': 'commit', 'text': 'ok'})
        self.assertEqual(self.message_from_server(), (100, 1, struct.pack('<I', 7) + wl_string('ok')))

    def test_text_after_the_field_changes_goes_to_the_new_field(self):
        self.focus()
        self.server.sendall(message(3, 1, struct.pack('<I', 100)) + message(3, 0, struct.pack('<I', 101))
                            + message(101, 4, struct.pack('<I', 9)))
        self.assertEqual(self.read(8), message(100, 0))
        self.type({'op': 'commit', 'text': 'x'})
        self.assertEqual(self.message_from_server(), (101, 1, struct.pack('<I', 9) + wl_string('x')))


if __name__ == '__main__':
    unittest.main()
