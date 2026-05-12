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
        devices logcat logcat-app logcat-auth logcat-clear logcat-dump stop uninstall deps deps-update doctor emu emu-pair emu-list kill-emus

help:
	@echo "Targets:"
	@echo "  make build              Assemble debug APK"
	@echo "  make install            Build + install debug APK on every attached device"
	@echo "  make run                Install + launch on every attached device"
	@echo "  make launch             Launch already-installed app on every attached device"
	@echo "  make debug              Install + launch with debugger waiting"
	@echo "  make stop               Force-stop the app"
	@echo "  make uninstall          Uninstall debug APK"
	@echo "                          (launch/debug/stop/uninstall: pass D=<serial> to target one device)"
	@echo "  make apk                Print path to built debug APK"
	@echo ""
	@echo "  make test              JVM unit tests (app/src/test)"vice
	@echo ""
	@echo "  make emu-list          List installed AVDs"
	@echo "  make emu AVD=<name>    Boot the named AVD in the background (omit AVD to use the first one)"
	@echo "  make emu-pair          Boot two emulator instances on ports 5554 + 5556 (for trainer/guardian side-by-side)"
	@echo "                         Defaults: AVD1=first listed, AVD2=second listed (or AVD1 twice with -read-only if only one exists)"
	@echo "                         Override: make emu-pair AVD1=Pixel_8 AVD2=Pixel_8a"
	@echo "                         Note: when sharing one AVD, BOTH instances must run -read-only — kill any existing"
	@echo "                         emulator first (make kill-emus) so they don't fight for the disk lock."
	@echo "  make kill-emus         Send 'emu kill' to every connected emulator"
	@echo "  make devices           List connected adb devices"
	@echo "  make logcat            Tail full logcat"
	@echo "  make logcat-app        Tail logcat filtered to this app's PID"
	@echo "  make logcat-auth       Tail logcat (all processes) filtered to auth tags"
	@echo "  make logcat-clear      Wipe the logcat buffer"
	@echo "  make logcat-dump       One-shot dump of recent logs filtered to auth tags (N=800)"
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

# Pick targets: if D=<serial> is set, just that one; otherwise every device in
# 'device' state. `installDebug` already installs on every attached device, so
# launch/debug/stop default to fanning out across all of them too — necessary
# when `make emu-pair` has two emulators up.
launch:
	@TARGETS="$${D:-$$("$(ADB)" devices | awk '$$2=="device"{print $$1}')}"; \
	if [ -z "$$TARGETS" ]; then echo "No devices attached."; exit 1; fi; \
	for s in $$TARGETS; do \
	  echo "Launching on $$s"; \
	  "$(ADB)" -s $$s shell am start -n $(ACTIVITY); \
	done

debug: install
	@TARGETS="$${D:-$$("$(ADB)" devices | awk '$$2=="device"{print $$1}')}"; \
	if [ -z "$$TARGETS" ]; then echo "No devices attached."; exit 1; fi; \
	for s in $$TARGETS; do \
	  echo "Launching (debug) on $$s"; \
	  "$(ADB)" -s $$s shell am start -D -n $(ACTIVITY); \
	done

stop:
	@TARGETS="$${D:-$$("$(ADB)" devices | awk '$$2=="device"{print $$1}')}"; \
	if [ -z "$$TARGETS" ]; then echo "No devices attached."; exit 1; fi; \
	for s in $$TARGETS; do \
	  echo "Stopping on $$s"; \
	  "$(ADB)" -s $$s shell am force-stop $(PACKAGE); \
	done

uninstall:
	@TARGETS="$${D:-$$("$(ADB)" devices | awk '$$2=="device"{print $$1}')}"; \
	if [ -z "$$TARGETS" ]; then echo "No devices attached."; exit 1; fi; \
	for s in $$TARGETS; do \
	  echo "Uninstalling on $$s"; \
	  "$(ADB)" -s $$s uninstall $(PACKAGE) || true; \
	done

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

# Boot two emulator instances side-by-side — useful for trainer/guardian dual-account testing.
# Each instance shows up in `adb devices` separately (emulator-5554 and emulator-5556).
# Same-AVD mode requires -read-only on BOTH instances (the emulator refuses parallel
# instances of one AVD unless every one is read-only). Distinct-AVD mode boots normally.
# If you have a stale emulator running, `make kill-emus` first.
emu-pair:
	@AVDS=$$("$(EMULATOR)" -list-avds); \
	AVD1=$${AVD1:-$$(echo "$$AVDS" | sed -n '1p')}; \
	AVD2=$${AVD2:-$$(echo "$$AVDS" | sed -n '2p')}; \
	if [ -z "$$AVD1" ]; then echo "No AVDs installed. Create one in Android Studio Device Manager."; exit 1; fi; \
	if [ -z "$$AVD2" ] || [ "$$AVD1" = "$$AVD2" ]; then \
	  echo "Single-AVD mode: booting two read-only instances of $$AVD1 (state isolated per session, not persisted)."; \
	  nohup "$(EMULATOR)" -avd "$$AVD1" -port 5554 -read-only >/tmp/emulator-5554-$$AVD1.log 2>&1 & \
	  nohup "$(EMULATOR)" -avd "$$AVD1" -port 5556 -read-only >/tmp/emulator-5556-$$AVD1.log 2>&1 & \
	else \
	  echo "Booting #1: $$AVD1 on port 5554"; \
	  nohup "$(EMULATOR)" -avd "$$AVD1" -port 5554 >/tmp/emulator-5554-$$AVD1.log 2>&1 & \
	  echo "Booting #2: $$AVD2 on port 5556"; \
	  nohup "$(EMULATOR)" -avd "$$AVD2" -port 5556 >/tmp/emulator-5556-$$AVD2.log 2>&1 & \
	fi; \
	echo "Both booting. Run 'make devices' once they finish; logs in /tmp/emulator-555{4,6}-*.log."

# Kill every currently-attached emulator. Useful before `make emu-pair` if you have a
# stale single instance running that holds the AVD's disk lock.
kill-emus:
	@for s in $$("$(ADB)" devices | awk '/^emulator-/{print $$1}'); do \
	  echo "Killing $$s"; "$(ADB)" -s $$s emu kill || true; \
	done

devices:
	"$(ADB)" devices -l

logcat:
	"$(ADB)" logcat

# Tail logcat scoped to this app's current PID (re-run after each install).
# With multiple devices attached, set D=<serial> to pick one (e.g. D=emulator-5554).
logcat-app:
	@TARGETS="$${D:-$$("$(ADB)" devices | awk '$$2=="device"{print $$1}')}"; \
	if [ -z "$$TARGETS" ]; then echo "No devices attached."; exit 1; fi; \
	COUNT=$$(echo "$$TARGETS" | wc -w | tr -d ' '); \
	if [ "$$COUNT" -gt 1 ]; then \
	  echo "Multiple devices attached. Pass D=<serial> (one of: $$TARGETS)."; exit 1; \
	fi; \
	PID=$$("$(ADB)" -s $$TARGETS shell pidof -s $(PACKAGE) 2>/dev/null); \
	if [ -z "$$PID" ]; then echo "App not running on $$TARGETS. 'make run' first."; exit 1; fi; \
	"$(ADB)" -s $$TARGETS logcat --pid=$$PID

# Tail logcat across all processes for auth-flow diagnostics.
# Captures both our app and the system Google Play Services process where the
# real Credential Manager / Google Sign-In errors are logged.
logcat-auth:
	"$(ADB)" logcat -c
	"$(ADB)" logcat | grep --line-buffered -iE 'HoundHabitAuth|CredMan|GoogleApi|GoogleId|gms\.auth|gms\.signin|signinwithgoogle|IdentityCredential'

# One-shot dump of the last N lines of logcat (default 800), filtered to auth tags.
# Workflow: `make logcat-clear`, do the failing action in the app, then `make logcat-dump`.
logcat-clear:
	"$(ADB)" logcat -c

logcat-dump:
	"$(ADB)" logcat -d -t $${N:-800} | grep -iE 'HoundHabitAuth|CredMan|GoogleApi|GoogleId|gms\.auth|gms\.signin|signinwithgoogle|IdentityCredential'

deps:
	$(GRADLE) :app:dependencies --configuration debugRuntimeClasspath

deps-update:
	$(GRADLE) dependencyUpdates -Drevision=release

doctor:
	@echo "== gradle =="; $(GRADLE) --version
	@echo; echo "== java =="; java -version 2>&1
	@echo; echo "== adb =="; "$(ADB)" version 2>&1 || echo "(adb not found at $(ADB))"
	@echo; echo "== sdk =="; echo "$(SDK_DIR)"
