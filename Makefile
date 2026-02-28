.PHONY: build build-release test clean verify emulator install run help

AVD_NAME=test_avd
export ANDROID_AVD_HOME=/home/yuval/.config/.android/avd/

# Default target
all: verify

help:
	@echo "Available commands:"
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

build:
	./gradlew assembleDebug --no-daemon

build-release:
	./gradlew assembleRelease --no-daemon

test:
	./gradlew testDebugUnitTest --no-daemon

lint:
	./gradlew lintDebug -PwarningsAsErrors=true --warning-mode all --no-daemon

deps:
	./gradlew dependencyUpdates --no-daemon

clean:
	./gradlew clean --no-daemon

verify: clean lint test build

emulator:
	@echo "Starting emulator $(AVD_NAME)..."
	@$(ANDROID_HOME)/emulator/emulator -avd $(AVD_NAME) -no-snapshot-load > /dev/null 2>&1 &

install:
	./gradlew installDebug --no-daemon

run: install
	adb shell am start -n com.yuval.podcasts/.MainActivity
