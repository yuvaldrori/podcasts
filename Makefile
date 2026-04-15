.PHONY: init build build-release test clean verify emulator stop-emulator install run help avd-init

# Android SDK path
ANDROID_HOME ?= /home/yuval/Android
export ANDROID_HOME
export ANDROID_SDK_ROOT = $(ANDROID_HOME)

AVD_NAME=pixel_8_pro
SYSTEM_IMAGE="system-images;android-36.1;google_apis;x86_64"
AVD_MANAGER=$(ANDROID_HOME)/cmdline-tools/bin/avdmanager
EMULATOR=$(ANDROID_HOME)/emulator/emulator
SDK_MANAGER=$(ANDROID_HOME)/cmdline-tools/bin/sdkmanager
ADB=$(ANDROID_HOME)/platform-tools/adb

# Default target
all: verify

help:
	@echo "Available commands:"
	@echo "  make init	 - Initialize environment (accept licenses, install SDK)"
	@echo "  make avd-init	 - Install system image and create Pixel 8 Pro AVD"
	@echo "  make build	 - Assemble debug APK"
	@echo "  make build-release - Assemble release APK"
	@echo "  make test	  - Run unit tests"
	@echo "  make lint	  - Run Android Lint (strict mode)"
	@echo "  make deps	  - Check for dependency updates"
	@echo "  make clean	 - Clean build artifacts"
	@echo "  make verify	- Run clean, lint, test, and debug build"
	@echo "  make emulator      - Start the Android emulator in background (KVM + Audio)"
	@echo "  make stop-emulator - Stop the running Android emulator"
	@echo "  make install       - Install the debug APK on connected device"
	@echo "  make run	   - Install and launch the app"

init:
	@echo "Initializing environment..."
	chmod +x gradlew
	yes | $(SDK_MANAGER) --sdk_root=$(ANDROID_HOME) --licenses
	$(SDK_MANAGER) --sdk_root=$(ANDROID_HOME) "platforms;android-36" "build-tools;36.1.0" "emulator" "platform-tools"
	# Ensure the latest minor SDK is also available if needed by the toolchain
	@echo "Environment initialized. Gradle will automatically handle JDK 21 if configured."

avd-init:
	@echo "Installing system image and creating Pixel 8 Pro AVD..."
	yes | $(SDK_MANAGER) --sdk_root=$(ANDROID_HOME) $(SYSTEM_IMAGE) "platform-tools"
	echo "no" | $(AVD_MANAGER) create avd -n $(AVD_NAME) -k $(SYSTEM_IMAGE) -d "pixel_8_pro" --force
	@# Fix the image path in config.ini if it was created with an extra "Android/" prefix
	sed -i 's|image.sysdir.1=Android/system-images/|image.sysdir.1=system-images/|' ~/.android/avd/$(AVD_NAME).avd/config.ini
	@echo "AVD $(AVD_NAME) created and path fixed."

build:
	./gradlew :app:assembleDebug

build-release:
	./gradlew :app:assembleRelease

test:
	./gradlew :app:testDebugUnitTest

lint:
	./gradlew :app:lintDebug -PwarningsAsErrors=true --warning-mode all

deps:
	./gradlew :app:dependencyUpdates --no-parallel --no-configuration-cache

clean:
	./gradlew clean

verify: clean lint test build
# Emulator configuration
CORES ?= 2
RAM ?= 3072

emulator:
	@echo "Starting Pixel 8 Pro emulator ($(CORES) cores, $(RAM)MB RAM)..."
	@nohup $(EMULATOR) -avd $(AVD_NAME) \
		-no-snapshot \
		-gpu swiftshader_indirect \
		-memory $(RAM) \
		-cores $(CORES) \
		-qemu -enable-kvm > emulator_startup.log 2>&1 &
	@echo "Emulator is starting in background. Check emulator_startup.log for progress."


stop-emulator:
	@echo "Stopping emulator..."
	@$(ADB) emu kill 2>/dev/null || true
	@pkill -f "emulator" 2>/dev/null || true
	@pkill -f "qemu-system" 2>/dev/null || true
	@sleep 2
	@echo "Emulator stopped."

install:
	./gradlew :app:installDebug

run: install
	$(ADB) shell am start -n com.yuval.podcasts/.MainActivity

benchmark-run:
	./gradlew :benchmark:connectedBenchmarkAndroidTest

