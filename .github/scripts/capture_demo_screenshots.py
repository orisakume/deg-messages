"""Capture real app screens on a clean emulator using synthetic messages only."""

from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "release" / "DEG-Messages-v1.0.0-universal.apk"
OUTPUT = ROOT / "docs" / "screenshots"
PACKAGE = "com.example.messages"


def command(*args: str, timeout: int = 30, check: bool = True) -> str:
    result = subprocess.run(
        ["adb", *args],
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    if check and result.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def insert_contact(first: str, last: str, number: str) -> None:
    try:
        raw_result = command(
            "shell", "content", "insert", "--uri",
            "content://com.android.contacts/raw_contacts",
        )
        match = re.search(r"row id=(\d+)", raw_result)
        if not match:
            raise RuntimeError("contacts provider did not return a raw contact ID")
        raw_id = match.group(1)
        command(
            "shell", "content", "insert", "--uri",
            "content://com.android.contacts/data",
            "--bind", f"raw_contact_id:i:{raw_id}",
            "--bind", "mimetype:s:vnd.android.cursor.item/name",
            "--bind", f"data1:s:{first}", "--bind", f"data2:s:{last}",
        )
        command(
            "shell", "content", "insert", "--uri",
            "content://com.android.contacts/data",
            "--bind", f"raw_contact_id:i:{raw_id}",
            "--bind", "mimetype:s:vnd.android.cursor.item/phone_v2",
            "--bind", f"data1:s:{number}", "--bind", "data2:i:2",
        )
    except (RuntimeError, subprocess.SubprocessError) as error:
        # Names are a visual enhancement; synthetic phone numbers remain safe.
        print(f"Contact label skipped for {number}: {error}")


def dump_hierarchy() -> ET.Element:
    command("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=30)
    xml = subprocess.run(
        ["adb", "exec-out", "cat", "/sdcard/window.xml"],
        capture_output=True,
        timeout=15,
        check=True,
    ).stdout
    return ET.fromstring(xml)


def visible_text() -> list[str]:
    return [node.attrib.get("text", "") for node in dump_hierarchy().iter("node") if node.attrib.get("text")]


def wait_for_text(fragment: str, timeout: int = 25) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if any(fragment.casefold() in text.casefold() for text in visible_text()):
            return
        time.sleep(1)
    raise RuntimeError(f"Timed out waiting for {fragment!r}; visible text: {visible_text()}")


def tap_text(fragment: str) -> None:
    root = dump_hierarchy()
    parents = {child: parent for parent in root.iter() for child in parent}
    candidates = [
        node for node in root.iter("node")
        if fragment.casefold() in node.attrib.get("text", "").casefold()
        or fragment.casefold() in node.attrib.get("content-desc", "").casefold()
    ]
    if not candidates:
        raise RuntimeError(f"Could not find {fragment!r}; visible text: {visible_text()}")
    node = candidates[0]
    while node is not None and node.attrib.get("clickable") != "true":
        node = parents.get(node)
    if node is None:
        node = candidates[0]
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not bounds:
        raise RuntimeError(f"No screen bounds found for {fragment!r}")
    x1, y1, x2, y2 = map(int, bounds.groups())
    command("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)


def capture(name: str) -> None:
    time.sleep(2)
    result = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=30, check=True)
    if not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Emulator returned an invalid PNG for {name}")
    (OUTPUT / name).write_bytes(result.stdout)
    print(f"Captured synthetic demo screen: {name} ({len(result.stdout)} bytes)")


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    command("install", "-r", str(APK), timeout=180)
    command("shell", "cmd", "role", "add-role-holder", "--user", "0", "android.app.role.SMS", PACKAGE)
    command("shell", "pm", "grant", PACKAGE, "android.permission.READ_CONTACTS", check=False)
    command("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)

    contacts = [
        ("Alex", "Rivera", "202-555-0141"),
        ("Maya", "Chen", "202-555-0142"),
        ("Jordan", "Lee", "202-555-0143"),
    ]
    for first, last, number in contacts:
        insert_contact(first, last, number)

    command("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", timeout=45)
    time.sleep(3)
    messages = [
        ("2025550141", "Hi! Are we still on for coffee after work?"),
        ("2025550141", "Absolutely. 6:30 at the little cafe on Oak?"),
        ("2025550142", "Your verification code is 482913. It expires in 10 minutes."),
        ("2025550143", "The design review moved to 3 PM. See you there."),
    ]
    for sender, body in messages:
        command("emu", "sms", "send", sender, body, timeout=30)
        time.sleep(2)

    wait_for_text("Messages")
    wait_for_text("Alex")
    capture("01-inbox.png")

    tap_text("Alex")
    time.sleep(3)
    capture("02-conversation.png")

    command("shell", "input", "keyevent", "4")
    time.sleep(2)
    tap_text("OTPs")
    wait_for_text("OTP detected")
    capture("03-otps.png")

    tap_text("Settings")
    wait_for_text("Settings")
    capture("04-settings.png")


if __name__ == "__main__":
    main()
