#!/usr/bin/env python3
"""
Captures ONLY the JediTerm Compose window without any background apps or desktop.
Uses PyObjC to find the window ID and screencapture to capture it.
"""
import subprocess
import sys
import Quartz

def find_jediterm_window_id():
    """Find the window ID of the JediTerm Compose application by PID."""
    # Find Java process running MainKt
    result = subprocess.run(['ps', 'aux'], capture_output=True, text=True)
    jediterm_pid = None

    for line in result.stdout.splitlines():
        if 'MainKt' in line and 'java' in line:
            parts = line.split()
            jediterm_pid = int(parts[1])
            print(f"Found JediTerm Java process: PID {jediterm_pid}")
            break

    if not jediterm_pid:
        print("❌ JediTerm Java process not found")
        return None

    # Get all windows (including some internal ones)
    # Use kCGWindowListOptionAll to find all windows, then filter by OnScreen
    window_list = Quartz.CGWindowListCopyWindowInfo(
        Quartz.kCGWindowListOptionAll,
        Quartz.kCGNullWindowID
    )

    if not window_list:
        print("❌ Failed to get window list")
        return None

    # Find window matching our PID (filter for windows with JediTerm title)
    for window in window_list:
        window_pid = window.get('kCGWindowOwnerPID')
        window_name = window.get('kCGWindowName', '')
        window_id = window.get('kCGWindowNumber')

        # Only consider windows with the JediTerm title
        if window_pid == jediterm_pid and 'JediTerm' in window_name:
            print(f"Found JediTerm window:")
            print(f"  - Window ID: {window_id}")
            print(f"  - Window Name: {window_name}")
            print(f"  - PID: {window_pid}")
            return window_id

    print(f"❌ No window found for PID {jediterm_pid}")
    return None

def capture_window(window_id, output_path="/tmp/jediterm_window.png"):
    """Capture a specific window by ID using screencapture."""
    try:
        # Use screencapture -l to capture specific window
        # -o flag excludes shadow
        subprocess.run(
            ['screencapture', '-l', str(window_id), '-o', output_path],
            check=True
        )
        print(f"✓ Screenshot saved: {output_path}")
        subprocess.run(['ls', '-lh', output_path])
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ Failed to capture window: {e}")
        return False

def main():
    print("Capturing JediTerm window only (no background apps)...")
    print()

    # Find window ID
    window_id = find_jediterm_window_id()
    if not window_id:
        sys.exit(1)

    print()

    # Capture the window
    if capture_window(window_id):
        sys.exit(0)
    else:
        sys.exit(1)

if __name__ == '__main__':
    main()
