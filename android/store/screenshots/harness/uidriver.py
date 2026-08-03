#!/usr/bin/env python3
"""Tap Android UI by visible text rather than by screen fraction.

Hardcoded fractions only ever worked on the phone: the tool sheet lays out
differently at tablet aspect, so the same fraction hit a different row. This
resolves the node from a uiautomator dump and taps its centre, which is
device-independent. It also clears the "isn't responding" ANR dialog that the
launcher throws when the emulator is loaded, since that swallows every tap.
"""
import re
import subprocess
import sys
import time

BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def sh(*args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, timeout=timeout).stdout


def dump():
    for _ in range(3):
        out = sh("exec-out", "uiautomator", "dump", "/dev/tty").decode("utf-8", "replace")
        if "<hierarchy" in out:
            return out
        time.sleep(1.0)
    return ""


def nodes(xml):
    for m in re.finditer(r"<node\b[^>]*/?>", xml):
        tag = m.group(0)
        b = BOUNDS.search(tag)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        text = re.search(r'text="([^"]*)"', tag)
        desc = re.search(r'content-desc="([^"]*)"', tag)
        yield (text.group(1) if text else "",
               desc.group(1) if desc else "",
               (x1 + x2) // 2, (y1 + y2) // 2)


def find(label, xml=None):
    """Centre of the first node whose text or content-desc contains `label`."""
    xml = xml if xml is not None else dump()
    low = label.lower()
    for text, desc, cx, cy in nodes(xml):
        if low in text.lower() or low in desc.lower():
            return cx, cy
    return None


def dismiss_anr():
    """Clear a system 'isn't responding' dialog if one is up."""
    xml = dump()
    if "isn't responding" not in xml and "isn’t responding" not in xml:
        return False
    for label in ("Wait", "Close app"):
        p = find(label, xml)
        if p:
            sh("shell", "input", "tap", str(p[0]), str(p[1]))
            time.sleep(2)
            return True
    return False


def screen_size():
    out = sh("shell", "wm", "size").decode().strip()
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def scroll_down():
    w, h = screen_size()
    sh("shell", "input", "swipe", str(w // 2), str(int(h * 0.72)),
       str(w // 2), str(int(h * 0.40)), "450")
    time.sleep(1.2)


def tap_text(label, wait=2.5, tries=6, scrolls=3):
    """Poll for a node and tap it, scrolling if it starts below the fold.

    On the tablets the primary action sits under the taller hero photo, so it is
    absent from the dump until the page is scrolled — which is why a pure
    poll-and-retry reported it missing.
    """
    for attempt in range(tries):
        dismiss_anr()
        p = find(label)
        if p:
            sh("shell", "input", "tap", str(p[0]), str(p[1]))
            time.sleep(wait)
            return True
        if attempt < scrolls:
            scroll_down()
        else:
            time.sleep(1.5)
    print(f"    !! not found: {label}", file=sys.stderr)
    return False


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap":
        ok = tap_text(sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 2.5)
        sys.exit(0 if ok else 1)
    if cmd == "anr":
        print("dismissed" if dismiss_anr() else "none")
    if cmd == "has":
        sys.exit(0 if find(sys.argv[2]) else 1)
