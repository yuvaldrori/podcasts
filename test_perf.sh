adb logcat -c
adb shell "dumpsys gfxinfo com.yuval.podcasts reset"
adb shell am start -n com.yuval.podcasts/.MainActivity
sleep 3
adb shell input tap 18 584
sleep 1
adb shell input tap 106 584
sleep 1
adb shell input tap 173 584
sleep 1
adb shell input tap 258 584
sleep 1
adb shell input tap 106 584
sleep 2
adb logcat -d | grep -i "slow"
adb shell "dumpsys gfxinfo com.yuval.podcasts" | grep -A 15 "Janky frames"
