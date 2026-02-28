# Android swipe command sends ACTION_DOWN, ACTION_MOVE, ACTION_UP. If the duration is long, it might trigger long press.
# If detectReorder(state) requires a drag gesture without long press, maybe we need to drag exactly on the handle.
# The handle is at x=268, y=160.
adb shell input swipe 268 160 268 56 1000
