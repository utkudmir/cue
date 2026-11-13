import Foundation
import Security
import Shared
import XCTest

@MainActor
final class SecureTokenStoreTests: XCTestCase {
    func testWritesUpdatesReadsAndClearsAuthInKeychain() async throws {
        let service = "com.utkudemir.cue.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: service))
        defer { cleanUp(service: service, defaults: defaults) }
        let store = makeStore(defaults: defaults, service: service)
        let state = testState()

        let initial = try await store.read()
        XCTAssertNil(initial)
        try await store.write(state: state)
        let saved = try await store.read()
        XCTAssertEqual(state, saved)

        let reopened = makeStore(defaults: defaults, service: service)
        let persisted = try await reopened.read()
        XCTAssertEqual(state, persisted)

        let updated = testState(accessToken: "updated-test-token")
        try await reopened.write(state: updated)
        let refreshed = try await store.read()
        XCTAssertEqual(updated, refreshed)
        XCTAssertNil(defaults.string(forKey: "auth_state_json"))

        try await store.clear()
        let cleared = try await reopened.read()
        XCTAssertNil(cleared)
    }

    func testMigratesLegacyDefaultsIntoKeychain() async throws {
        let service = "com.utkudemir.cue.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: service))
        defer { cleanUp(service: service, defaults: defaults) }
        let data = try JSONSerialization.data(withJSONObject: [
            "accessToken": "test-access-token",
            "refreshToken": "test-refresh-token",
            "clientId": "test-client-id",
            "clientSecret": "test-client-secret",
            "accessTokenExpiresAt": "2030-01-01T00:00:00Z"
        ])
        defaults.set(String(data: data, encoding: .utf8), forKey: "auth_state_json")

        let store = makeStore(defaults: defaults, service: service)
        let migrated = try await store.read()
        XCTAssertEqual(testState(), migrated)
        XCTAssertNil(defaults.string(forKey: "auth_state_json"))

        let reopened = makeStore(defaults: defaults, service: service)
        let persisted = try await reopened.read()
        XCTAssertEqual(testState(), persisted)
    }

    private func makeStore(defaults: UserDefaults, service: String) -> SecureTokenStoreImpl {
        SecureTokenStoreImpl(
            defaults: defaults,
            json: Kotlinx_serialization_jsonJson.companion,
            keychainService: service
        )
    }

    private func testState(accessToken: String = "test-access-token") -> StoredAuthState {
        StoredAuthState(
            accessToken: accessToken,
            refreshToken: "test-refresh-token",
            clientId: "test-client-id",
            clientSecret: "test-client-secret",
            accessTokenExpiresAt: Kotlinx_datetimeInstant.companion.fromEpochMilliseconds(
                epochMilliseconds: 1_893_456_000_000
            )
        )
    }

    private func cleanUp(service: String, defaults: UserDefaults) {
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "stored_auth_state"
        ] as CFDictionary)
        defaults.removePersistentDomain(forName: service)
    }
}
