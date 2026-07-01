.PHONY: init build build-release test clean verify emulator stop-emulator install run help describe layout screenshot

# Default target
all: verify

help:
	@echo "Available commands using Android CLI:"
	@echo "  make init	    - Initialize environment and install required SDKs"
	@echo "  make build	    - Assemble debug APK"
	@echo "  make build-release - Assemble release APK"
	@echo "  make test	    - Run unit tests"
	@echo "  make lint	    - Run Android Lint (strict mode)"
	@echo "  make clean	    - Clean build artifacts"
	@echo "  make verify	    - Run clean, lint, test, and debug build"
	@echo "  make emulator      - Start the Android emulator"
	@echo "  make stop-emulator - Stop the running emulator"
	@echo "  make run	    - Build, install and launch the app"
	@echo "  make describe      - Show project structure and build targets"
	@echo "  make layout        - Inspect the UI layout of the running app"
	@echo "  make screenshot    - Capture a screenshot of the device"

init:
	@echo "Initializing Android environment..."
	chmod +x gradlew
	android init
	# The app targets minSdk/compileSdk/targetSdk = API 37, so provision the API 37
	# platform, build tools, and emulator system image (the app won't install on older).
	android sdk install platforms/android-37.0 build-tools/37.0.0 emulator platform-tools system-images/android-37.0/google_apis_playstore_ps16k/x86_64

build:
	./gradlew :app:assembleDebug

build-release:
	./gradlew :app:assembleRelease

test:
	./gradlew :app:testDebugUnitTest

lint:
	./gradlew :app:lintDebug -PwarningsAsErrors=true --warning-mode all

clean:
	./gradlew clean

verify: clean lint test build
	@echo "Verification complete. Running layout check..."
	@make layout || echo "Layout check skipped (no device connected)"

AVD_NAME := podcasts_api37
SYS_IMG_37 := system-images;android-37.0;google_apis_playstore_ps16k;x86_64

emulator:
	@echo "Starting API 37 emulator ($(AVD_NAME))..."
	@if ! android emulator list | grep -qx "$(AVD_NAME)"; then \
		printf 'no\n' | "$$ANDROID_HOME/cmdline-tools/bin/avdmanager" create avd -n $(AVD_NAME) -k "$(SYS_IMG_37)" -d pixel_8 --force; \
		sed -i 's#^image.sysdir.1=Android/system-images#image.sysdir.1=system-images#' "$$HOME/.android/avd/$(AVD_NAME).avd/config.ini"; \
	fi
	android emulator start $(AVD_NAME)

stop-emulator:
	@echo "Stopping emulator..."
	android emulator stop $(AVD_NAME)

run:
	@echo "Deploying and running application..."
	android run --apks=app/build/outputs/apk/debug/app-debug.apk --activity=com.yuval.podcasts.MainActivity

describe:
	android describe

layout:
	android layout --pretty

screenshot:
	android screen capture -o docs/images/latest_screen.png

benchmark-run:
	./gradlew :benchmark:connectedBenchmarkAndroidTest
