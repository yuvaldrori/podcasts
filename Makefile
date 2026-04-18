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
	@echo "  make emulator      - Start the Pixel 8 Pro emulator"
	@echo "  make stop-emulator - Stop the running emulator"
	@echo "  make run	    - Build, install and launch the app"
	@echo "  make describe      - Show project structure and build targets"
	@echo "  make layout        - Inspect the UI layout of the running app"
	@echo "  make screenshot    - Capture a screenshot of the device"

init:
	@echo "Initializing Android environment..."
	chmod +x gradlew
	android init
	android sdk install platforms/android-36 build-tools/36.1.0 emulator platform-tools system-images/android-36/google_apis/x86_64

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

emulator:
	@echo "Starting Pixel 8 Pro emulator..."
	android emulator start pixel_8_pro || (android emulator create --name=pixel_8_pro --device="pixel_8_pro" && android emulator start pixel_8_pro)

stop-emulator:
	@echo "Stopping emulator..."
	android emulator stop pixel_8_pro

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
