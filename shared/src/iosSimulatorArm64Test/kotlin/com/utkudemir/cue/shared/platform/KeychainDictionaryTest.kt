package com.utkudemir.cue.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFBooleanGetTypeID
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDictionaryGetCount
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFStringGetTypeID
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class KeychainDictionaryTest {
    @Test
    fun readQueryPreservesNativeKeysAndCoreFoundationValues() {
        withKeychainDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to "com.utkudemir.cue.tests",
            kSecAttrAccount to "test-account",
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        ) { query ->
            assertEquals(5L, CFDictionaryGetCount(query))
            assertEquals(kSecClassGenericPassword, CFDictionaryGetValue(query, kSecClass))
            assertEquals(kSecMatchLimitOne, CFDictionaryGetValue(query, kSecMatchLimit))
            assertEquals(CFStringGetTypeID(), CFGetTypeID(CFDictionaryGetValue(query, kSecAttrService)))
            assertEquals(CFStringGetTypeID(), CFGetTypeID(CFDictionaryGetValue(query, kSecAttrAccount)))
            assertEquals(CFBooleanGetTypeID(), CFGetTypeID(CFDictionaryGetValue(query, kSecReturnData)))
        }
    }

    @Test
    fun writeQueryBridgesFoundationDataAsNativeData() {
        withKeychainDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecValueData to NSData()
        ) { query ->
            assertEquals(2L, CFDictionaryGetCount(query))
            assertEquals(CFDataGetTypeID(), CFGetTypeID(CFDictionaryGetValue(query, kSecValueData)))
        }
    }
}
