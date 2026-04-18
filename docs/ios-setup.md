# iOS Setup

DebridHub keeps the iOS app inside the same repository under `iosApp/`. The
shared Kotlin Multiplatform code lives in `shared/`, and the native SwiftUI
host app links the generated `Shared.framework` during the Xcode build.

## Requirements

- macOS with Xcode 15 or newer
- JDK 21
- `xcodegen` installed with `brew install xcodegen`

## Root-level commands

Generate the Xcode project:

```bash
make ios-project
```

Open the iOS project in Xcode:

```bash
make ios-open
```

Build for the current simulator from the repo root:

```bash
make ios-build
```

Run native iOS unit tests (XCTest) on simulator:

```bash
make ios-test
```

Build, install, and launch the app on the simulator:

```bash
make ios-run
```

Dynamic iPhone resolution is the default. You can override the preferred phone
class or force a specific simulator when needed:

```bash
IOS_DEVICE_CLASS=latest-phone make ios-run
IOS_DEVICE_CLASS=small-phone make ios-run
IOS_SIMULATOR_NAME="<available-simulator-name>" make ios-run
```

The same simulator selection overrides also apply to tests:

```bash
IOS_DEVICE_CLASS=latest-phone make ios-test
IOS_DEVICE_CLASS=small-phone make ios-test
IOS_SIMULATOR_NAME="<available-simulator-name>" make ios-test
```

Current iOS native regression suite includes `DebridHubHostTests` with
`IOSAppViewModelTests` parity scenarios.

## How the integration works

1. `iosApp/DebridHubHost.xcodeproj` is generated from `iosApp/project.yml`.
2. The Xcode target runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
   in a pre-build script.
3. The generated `Shared.framework` is linked from
   `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
4. The SwiftUI host app imports `Shared` and uses the KMP controller and graph
   types exposed by the `shared` module.
