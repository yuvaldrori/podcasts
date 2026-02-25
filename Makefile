.PHONY: build build-release test clean verify help

# Default target
all: verify

help:
	@echo "Available commands:"
	@echo "  make build         - Assemble debug APK"
	@echo "  make build-release - Assemble release APK"
	@echo "  make test          - Run unit tests"
	@echo "  make clean         - Clean build artifacts"
	@echo "  make verify        - Run clean, debug build, and test"

build:
	./gradlew assembleDebug --no-daemon

build-release:
	./gradlew assembleRelease --no-daemon

test:
	./gradlew testDebugUnitTest --no-daemon

clean:
	./gradlew clean --no-daemon

verify: clean build test
