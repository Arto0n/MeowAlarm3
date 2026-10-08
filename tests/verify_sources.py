"""Fast source checks; these do not replace an Android Gradle compilation."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class ProjectChecks(unittest.TestCase):
    def test_manifest_parses(self):
        ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')

    def test_required_gradle_files(self):
        for name in ('settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts'):
            self.assertTrue((ROOT / name).is_file(), name)

    def test_workflow_builds_at_repository_root(self):
        workflow = (ROOT / '.github/workflows/build-apk.yml').read_text()
        self.assertNotIn('working-directory: MeowAlarm', workflow)
        self.assertIn('test -f settings.gradle.kts', workflow)
        self.assertIn('platforms;android-34', workflow)
        self.assertIn('path: app/build/outputs/apk/debug/*.apk', workflow)

    def test_webview_javascript_syntax(self):
        html = (ROOT / 'app/src/main/assets/index.html').read_text()
        scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, flags=re.DOTALL)
        self.assertTrue(scripts)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'app.js'
            path.write_text('\n'.join(scripts))
            subprocess.run(['node', '--check', str(path)], check=True, capture_output=True, text=True)

    def test_webview_does_not_overwrite_native_settings_on_load(self):
        html = (ROOT / 'app/src/main/assets/index.html').read_text()
        self.assertIn('Android.getAlarmState()', html)
        self.assertNotIn("document.querySelector('.note').style.display='none';save();", html)

    def test_native_alarm_permissions(self):
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        for permission in ('USE_EXACT_ALARM', 'RECEIVE_BOOT_COMPLETED', 'FOREGROUND_SERVICE'):
            self.assertIn('android.permission.' + permission, manifest)


if __name__ == '__main__':
    unittest.main(verbosity=2)
