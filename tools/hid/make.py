"""Build a JSON script for Android's `hid` tool: a virtual Google TV remote (18d1:9451)
that presses HID Consumer usages like real hardware, so presses go through accessibility
key filters (adb `input keyevent` does not).

  python -I tools/hid/make.py 0x223 > home.json      # AC Home -> KEYCODE_HOME
  python -I tools/hid/make.py 0x186 0x186 > gh.json   # Google Home button (MACRO_1) twice
  adb push home.json /data/local/tmp/ && adb shell hid /data/local/tmp/home.json

Usages: 0x223 Home, 0x224 Back, 0x186 Google Home (MACRO_1), 0x77 YouTube, 0x78 Netflix,
0xE9/0xEA volume up/down, 0xE2 mute, 0xCD play/pause.
"""
import json
import sys

DESCRIPTOR = [0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00,
              0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00, 0xC0]

cmds = [{"id": 1, "command": "register", "name": "tvhome-test-remote", "vid": 0x18D1, "pid": 0x9451,
         "bus": "bluetooth", "descriptor": DESCRIPTOR},
        {"id": 1, "command": "delay", "duration": 1500}]
for arg in sys.argv[1:]:
    u = int(arg, 0)
    cmds += [{"id": 1, "command": "report", "report": [u & 0xFF, u >> 8]},
             {"id": 1, "command": "delay", "duration": 120},
             {"id": 1, "command": "report", "report": [0, 0]},
             {"id": 1, "command": "delay", "duration": 2500}]
print("\n".join(json.dumps(c) for c in cmds))
