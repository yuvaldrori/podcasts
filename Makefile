.PHONY: init build build-release test clean verify emulator install run help

AVD_NAME=test_avd
export ANDROID_AVD_HOME=$(HOME)/.config/.android/avd/
SDK_MANAGER=$(ANDROID_HOME)/cmdline-tools/bin/sdkmanager

# Default target
all: verify

help:
	@echo "Available commands:"
	@echo "  make init	 - Initialize environment (accept licenses, install SDK)"
	@echo "  make build	 - Assemble debug APK"
	@echo "  make build-release - Assemble release APK"
	@echo "  make test	  - Run unit tests"
	@echo "  make lint	  - Run Android Lint (strict mode)"
	@echo "  make deps	  - Check for dependency updates"
	@echo "  make clean	 - Clean build artifacts"
	@echo "  make verify	- Run clean, lint, test, and debug build"
	@echo "  make emulator      - Start the Android emulator in background"
	@echo "  make install       - Install the debug APK on connected device"
	@echo "  make run	   - Install and launch the app"

init:
	@echo "Initializing environment..."
	chmod +x gradlew
	yes | $(SDK_MANAGER) --sdk_root=$(ANDROID_HOME) --licenses
	$(SDK_MANAGER) --sdk_root=$(ANDROID_HOME) "platforms;android-36" "build-tools;36.1.0"
	# Ensure the latest minor SDK is also available if needed by the toolchain
	@echo "Environment initialized. Gradle will automatically handle JDK 21 if configured."

build:
	./gradlew assembleDebug

build-release:
	./gradlew assembleRelease

test:
	./gradlew testDebugUnitTest

lint:
	./gradlew lintDebug -PwarningsAsErrors=true --warning-mode all

deps:
	./gradlew dependencyUpdates --no-parallel --no-configuration-cache

clean:
	./gradlew clean

verify: clean lint test build

emulator:
	@echo "Starting emulator $(AVD_NAME)..."
	@$(ANDROID_HOME)/emulator/emulator -avd $(AVD_NAME) -no-snapshot-load > /dev/null 2>&1 &

install:
	./gradlew installDebug

run: install
	adb shell am start -n com.yuval.podcasts/.MainActivity

benchmark-run:
	./gradlew :benchmark:connectedBenchmarkAndroidTest

