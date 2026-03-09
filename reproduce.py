import uiautomator2 as u2
import time

d = u2.connect('emulator-5554')
d.app_start('com.yuval.podcasts')
time.sleep(2)

print("Started")
