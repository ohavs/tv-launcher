"""Evaluate JavaScript in TV Home's YouTube WebView over Chrome DevTools Protocol.

Needs: `adb shell setprop log.tag.YouTube DEBUG` (then reopen YouTube), `pip install websocket-client`.
Usage: python tools/cdp.py "document.title"     (expression; the result is printed as JSON)
"""
import json
import os
import subprocess
import sys
import time
import urllib.request

import websocket

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
DEVICE = os.environ.get("TV", "10.0.0.43:5555")
PORT = 9223


def adb(*args):
    return subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True, text=True).stdout


def main():
    sockets = [l.split("@")[-1] for l in adb("shell", "cat /proc/net/unix").split() if "webview_devtools_remote" in l]
    if not sockets:
        sys.exit("no WebView DevTools socket (setprop log.tag.YouTube DEBUG, reopen YouTube)")
    adb("forward", f"tcp:{PORT}", f"localabstract:{sockets[-1]}")
    pages = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json"))
    page = next(p for p in pages if p.get("type") == "page")
    ws = websocket.create_connection(page["webSocketDebuggerUrl"], suppress_origin=True)
    expr = sys.argv[1]
    if expr == "--reload":  # --reload "<script run before the page's own>" ["url"] "<expression after load>"
        ws.send(json.dumps({"id": 4, "method": "Page.enable"}))
        ws.send(json.dumps({"id": 2, "method": "Page.addScriptToEvaluateOnNewDocument", "params": {"source": sys.argv[2]}}))
        url = sys.argv[3] if len(sys.argv) > 4 else None
        ws.send(json.dumps({"id": 3, "method": "Page.navigate" if url else "Page.reload", "params": {"url": url} if url else {}}))
        time.sleep(float(os.environ.get("WAIT", "15")))
        expr = sys.argv[-1]
    ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate", "params": {
        "expression": expr, "returnByValue": True, "awaitPromise": True}}))
    while True:
        msg = json.loads(ws.recv())
        if msg.get("id") == 1:
            res = msg["result"]
            print(json.dumps(res.get("result", {}).get("value", res), ensure_ascii=False, indent=1))
            break


if __name__ == "__main__":
    main()
