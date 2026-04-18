# Session Handoff

## Durum Ozeti
- CI billing/spending limiti acilana kadar CI/workflow tarafini parkta tutuyoruz.
- Uygulama kodu ve testlerde Android/iOS parity + stability + coverage odaginda ilerliyoruz.
- Coverage gate aktif: `LINE >= 70`, `BRANCH >= 55`.
- iOS native XCTest altyapisi aktif ve `IOSAppViewModel` senaryo paritesi Android ile hizalandi.

## Son Tamamlanan Isler

### 1) Pushlanan parity + auth stabilizasyonu
- Commit: `67a360b` (`origin/main`)
- iOS keychain `status=-50` gibi hatalarda sessiz self-heal.
- iOS onboarding/polling terminal state cleanup.
- Android/iOS akis ve mesaj parity iyilestirmeleri.

### 2) Pushlanan coverage/test guclendirme
- Commit: `d261431` (`origin/main`)
- Jacoco report + verification eklendi.
- Baseline korumasi: `LINE >= 70`, `BRANCH >= 55`.
- Shared + Android test kapsamı genisletildi.

### 3) Pushlanan iOS native test boslugu kapatma
- Commit: `41a162a` (`origin/main`)
- `iosApp/project.yml` uzerinden XCTest target eklendi.
- Xcode proje regenerate edildi.
- `IOSAppViewModel` icin temel state-transition testleri eklendi.

### 4) Pushlanan Android/iOS test parity hizalama
- Commit: `ea1b832` (`origin/main`)
- `IOSAppViewModelTests` kapsamı Android ViewModel senaryo seti ile hizalandi.
- Mevcut durumda ViewModel test sayisi parity: Android 16 / iOS 16.

### 5) Lock-step TDD genisletmesi
- `make ios-test` komutu ve `scripts/test-ios-sim.sh` eklendi.
- Cancel authorization safety slice'i Android+iOS icin eklendi.
- Diagnostics preview success slice'i Android+iOS icin eklendi.
- Mevcut local parity: Android 18 / iOS 18.

## Dogrulama Sonuclari (Son Session)
- `make shared-test` -> PASS
- `./gradlew :androidApp:lint :androidApp:testDebugUnitTest` -> PASS
- `make coverage` -> PASS
- `make ios-build` -> PASS
- `xcodebuild ... -scheme DebridHubHost ... test` -> PASS

## Mevcut Local Durum
- Calisma agaci temiz hedeflenir; yeni ise baslarken `git status` ile dogrula.
- CI/workflow dosyalarina dokunma (billing limiti acilana kadar).

## Sonraki Plan (TDD, Lock-Step)
Her madde Android + iOS icin birlikte, RED->GREEN->REFACTOR dikey slice olarak ilerlesin.

1. Cancel authorization safety:
   - Polling durur mu, onboarding/session state temizleniyor mu?
2. Authenticated bootstrap happy path:
   - Startup sonrasinda refresh/sync/preview etkileri dogrulansin.
3. Reminder mutation matrix:
   - `sevenDay`, `threeDay`, `oneDay`, `notifyOnExpiry`, `notifyAfterExpiry`.
4. Diagnostics preview success path:
   - Basarili yukleme ile preview/state dogrulansin.
5. Duplicate/in-flight guard davranislari:
   - Tekrarlayan start auth / diagnostics preview cagrilarinda idempotent davranis.
6. Notification edge parity:
   - Granted/already-granted/denied/failure metin ve akislarinin hizasi.

## Coverage Hedefi (Bir Sonraki Esik)
- Mevcut baseline korunurken testler stabilize oldugunda bir sonraki hedef:
  - `LINE >= 75`
  - `BRANCH >= 60`

## Teknik Notlar / Guardrail
- Aktif Gradle modulleri: `:shared` ve `:androidApp` (`composeApp/` legacy).
- iOS proje source of truth: `iosApp/project.yml` (xcodeproj regenerate edilir).
- iOS runtime: `DebridHubApp.swift` -> `IOSAppViewModel.swift` -> `IosAppGraph` -> shared `DebridHubController`.
- Shared orchestration: `shared/src/commonMain/kotlin/app/debridhub/shared/DebridHubController.kt`.
- Product boundary koru: OAuth device flow + `/rest/1.0/user` + local reminders/diagnostics.
- Eklenmeyecek alanlar: `/unrestrict/*`, `/downloads/*`, `/torrents/*`, `/streaming/*`.

## Yeni Session Baslatma Promptu (Kopyala-Yapistir)
```text
Bu repo icin once docs/session-handoff.md dosyasini oku ve sadece oradaki plan uzerinden devam et.
CI billing limiti acilana kadar CI/workflow tarafina dokunma; yalnizca uygulama kodu ve testlere odaklan.

Android ve iOS testlerini TDD (RED->GREEN->REFACTOR) ve lock-step parity ile ilerlet.
Her slice sonunda Android unit test, iOS XCTest ve coverage baseline dogrulamalarini calistir.

Coverage baseline'i koru: line >= 70, branch >= 55.
Slice'lar stabilize olunca 75/60 esigine gecis icin ayri bir degisiklik oner.
```
