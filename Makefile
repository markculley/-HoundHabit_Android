# Hound Habit Android — common dev commands.
# Keep this in sync with what we actually run; if a workflow changes, update the Makefile.

PACKAGE   := com.cometncloud.houndhabit
ACTIVITY  := $(PACKAGE)/.MainActivity
GRADLE    := ./gradlew
APK_DEBUG := app/build/outputs/apk/debug/app-debug.apk

# Resolve adb: prefer ANDROID_HOME / ANDROID_SDK_ROOT, fall back to sdk.dir in local.properties,
# then to whatever's on PATH.
SDK_DIR  := $(or $(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(shell awk -F= '/^sdk\.dir=/{print $$2}' local.properties 2>/dev/null))
ADB      := $(if $(SDK_DIR),$(SDK_DIR)/platform-tools/adb,adb)
EMULATOR := $(if $(SDK_DIR),$(SDK_DIR)/emulator/emulator,emulator)

.PHONY: help build install run launch debug clean rebuild test single-test test-instrumented lint check apk \
        devices logcat logcat-app stop uninstall deps deps-update doctor emu emu-list

help:
	@echo "Targets:"
	@echo "  make build              Assemble debug APK"
	@echo "  make install            Build + install debug APK on connected device/emulator"
	@echo "  make run                Install + launch on connected device/emulator"
	@echo "  make launch             Launch already-installed app"
	@echo "  make debug              Install + launch with debugger waiting"
	@echo "  make stop               Force-stop the app"
	@echo "  make uninstall          Uninstall debug APK from device"
	@echo "  make apk                Print path to built debug APK"
	@echo ""
	@echo "  make test              JVM unit tests (app/src/test)"
	@echo "  make test-instrumented Instrumented tests on connected device (app/src/androidTest)"
	@echo "  make lint              Android Lint"
	@echo "  make check             Lint + unit tests"
	@echo ""
	@echo "  make clean             Wipe app/build"
	@echo "  make rebuild           clean + build"
	@echo ""
	@echo "  make emu-list          List installed AVDs"
	@echo "  make emu AVD=<name>    Boot the named AVD in the background (omit AVD to use the first one)"
	@echo "  make devices           List connected adb devices"
	@echo "  make logcat            Tail full logcat"
	@echo "  make logcat-app        Tail logcat filtered to this app's PID"
	@echo ""
	@echo "  make deps              Print dependency tree (debug)"
	@echo "  make deps-update       Show available dependency updates (requires com.github.ben-manes.versions plugin)"
	@echo "  make doctor            Print Gradle/Java/adb versions and resolved SDK path"
	@echo ""
	@echo "  make T='pkg.Class.method' single-test     Run a single JVM test"

build:
	$(GRADLE) :app:assembleDebug

install:
	$(GRADLE) :app:installDebug

run: install launch

launch:
	"$(ADB)" shell am start -n $(ACTIVITY)

debug: install
	"$(ADB)" shell am start -D -n $(ACTIVITY)

stop:
	"$(ADB)" shell am force-stop $(PACKAGE)

uninstall:
	"$(ADB)" uninstall $(PACKAGE) || true

apk:
	@echo $(APK_DEBUG)

clean:
	$(GRADLE) :app:clean

rebuild: clean build

test:
	$(GRADLE) :app:testDebugUnitTest

# Usage: make T='com.cometncloud.houndhabit.SomeTest.someMethod' single-test
single-test:
	@if [ -z "$(T)" ]; then echo "Set T=<fully.qualified.Test.method>"; exit 1; fi
	$(GRADLE) :app:testDebugUnitTest --tests "$(T)"

test-instrumented:
	$(GRADLE) :app:connectedDebugAndroidTest

lint:
	$(GRADLE) :app:lintDebug

check: lint test

emu-list:
	"$(EMULATOR)" -list-avds

# Boot an emulator headlessly in the background. Override with `make emu AVD=Pixel_8`.
emu:
	@AVD=$${AVD:-$$("$(EMULATOR)" -list-avds | head -n1)}; \
	if [ -z "$$AVD" ]; then echo "No AVDs installed. Create one in Android Studio Device Manager."; exit 1; fi; \
	echo "Starting AVD: $$AVD"; \
	nohup "$(EMULATOR)" -avd "$$AVD" >/tmp/emulator-$$AVD.log 2>&1 & \
	echo "Booted in background (log: /tmp/emulator-$$AVD.log). Run 'make devices' to confirm."

devices:
	"$(ADB)" devices -l

logcat:
	"$(ADB)" logcat

# Tail logcat scoped to this app's current PID (re-run after each install).
logcat-app:
	@PID=$$("$(ADB)" shell pidof -s $(PACKAGE) 2>/dev/null); \
	if [ -z "$$PID" ]; then echo "App not running. 'make run' first."; exit 1; fi; \
	"$(ADB)" logcat --pid=$$PID

deps:
	$(GRADLE) :app:dependencies --configuration debugRuntimeClasspath

deps-update:
	$(GRADLE) dependencyUpdates -Drevision=release

doctor:
	@echo "== gradle =="; $(GRADLE) --version
	@echo; echo "== java =="; java -version 2>&1
	@echo; echo "== adb =="; "$(ADB)" version 2>&1 || echo "(adb not found at $(ADB))"
	@echo; echo "== sdk =="; echo "$(SDK_DIR)"
