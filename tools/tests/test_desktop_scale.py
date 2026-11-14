import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCALE = Path(__file__).resolve().parents[1] / 'linuxfs/overlay/usr/local/bin/droiddeck-desktop-scale'

FAKE_DOCTOR = r'''#!/usr/bin/env python3
import json, os, sys
log = os.environ["FAKE_DOCTOR_LOG"]
with open(log, "a") as handle:
    handle.write(json.dumps({"argv": sys.argv[1:], "display": os.environ.get("WAYLAND_DISPLAY"),
                             "backend": os.environ.get("KSCREEN_BACKEND"),
                             "inprocess": os.environ.get("KSCREEN_BACKEND_INPROCESS")}) + "\n")
if os.environ.get("FAKE_DOCTOR_FAIL"):
    print("could not apply", file=sys.stderr)
    sys.exit(1)
if sys.argv[1:] == ["-j"]:
    print(os.environ["FAKE_DOCTOR_CONFIG"])
'''


class DesktopScaleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.bin = root / 'bin'
        self.bin.mkdir()
        doctor = self.bin / 'kscreen-doctor'
        doctor.write_text(FAKE_DOCTOR)
        doctor.chmod(0o755)
        self.runtime = root / 'run'
        self.runtime.mkdir()
        (self.runtime / 'wayland-1').touch()
        self.log = root / 'doctor.log'
        self.config = {'outputs': [
            {'id': 1, 'name': 'WL-1', 'enabled': True, 'connected': True, 'scale': 1.0},
            {'id': 2, 'name': 'WL-2', 'enabled': False, 'connected': True, 'scale': 1.0},
        ]}

    def run_scale(self, *args, **extra):
        env = dict(os.environ, PATH='%s:%s' % (self.bin, os.environ['PATH']), XDG_RUNTIME_DIR=str(self.runtime),
                   WAYLAND_DISPLAY='wayland-0', FAKE_DOCTOR_LOG=str(self.log),
                   FAKE_DOCTOR_CONFIG=json.dumps(self.config), **extra)
        done = subprocess.run(['python3', str(SCALE), *args], env=env, capture_output=True, text=True, timeout=30)
        return done.returncode, json.loads(done.stdout)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_set_scales_every_enabled_output_on_kwins_display(self):
        code, result = self.run_scale('set', '150')
        self.assertEqual(code, 0)
        self.assertEqual(result, {'ok': True, 'outputs': [{'name': 'WL-1', 'scale': 1.5}]})
        calls = self.calls()
        self.assertEqual([c['argv'] for c in calls], [['-j'], ['output.WL-1.scale.1.5']])
        self.assertTrue(all(c['display'] == 'wayland-1' and c['backend'] == 'KWayland' and c['inprocess'] == '1'
                            for c in calls))

    def test_quarter_steps_keep_two_decimals(self):
        self.run_scale('set', '125')
        self.assertEqual(self.calls()[-1]['argv'], ['output.WL-1.scale.1.25'])
        self.run_scale('set', '200')
        self.assertEqual(self.calls()[-1]['argv'], ['output.WL-1.scale.2'])

    def test_get_reports_the_current_scale(self):
        self.config['outputs'][0]['scale'] = 1.75
        code, result = self.run_scale('get')
        self.assertEqual((code, result), (0, {'ok': True, 'outputs': [{'name': 'WL-1', 'scale': 1.75}]}))

    def test_out_of_range_scales_are_refused_without_touching_the_desktop(self):
        for value in ('40', '400', 'big'):
            code, result = self.run_scale('set', value)
            self.assertEqual(code, 2)
            self.assertFalse(result['ok'])
        self.assertEqual(self.calls(), [])

    def test_a_desktop_that_is_not_running_is_reported(self):
        (self.runtime / 'wayland-1').unlink()
        code, result = self.run_scale('set', '150')
        self.assertEqual(code, 3)
        self.assertEqual(result['error'], 'the KDE desktop is not running')

    def test_a_failing_kscreen_doctor_is_reported(self):
        code, result = self.run_scale('get', FAKE_DOCTOR_FAIL='1')
        self.assertEqual(code, 6)
        self.assertEqual(result['error'], 'could not apply')

    def test_no_enabled_output_is_reported(self):
        self.config['outputs'][0]['enabled'] = False
        code, result = self.run_scale('set', '150')
        self.assertEqual(code, 7)
        self.assertFalse(result['ok'])


if __name__ == '__main__':
    unittest.main()
