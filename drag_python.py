import subprocess
import time

def run(cmd):
    subprocess.run(cmd, shell=True)

# Generate a synthetic swipe that presses down, holds, and drags.
run("adb shell sendevent /dev/input/event2 3 57 0") # Tracking ID
run("adb shell sendevent /dev/input/event2 3 53 280") # X
run("adb shell sendevent /dev/input/event2 3 54 172") # Y
run("adb shell sendevent /dev/input/event2 1 330 1") # Touch down
run("adb shell sendevent /dev/input/event2 0 0 0") # Sync

time.sleep(1.0) # Hold for 1 second

# Drag slowly
for y in range(172, 84, -5):
    run(f"adb shell sendevent /dev/input/event2 3 54 {y}")
    run("adb shell sendevent /dev/input/event2 0 0 0")
    time.sleep(0.05)

run("adb shell sendevent /dev/input/event2 1 330 0") # Touch up
run("adb shell sendevent /dev/input/event2 3 57 -1") # Tracking ID
run("adb shell sendevent /dev/input/event2 0 0 0") # Sync
