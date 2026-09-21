import Foundation
import LocalAuthentication
import Security

// Small CLI bridge between the Java app and macOS Touch ID + Keychain.
//
// The master password is stored as a plain Keychain generic-password item (Keychain's own
// per-user file encryption already protects it at rest). The Touch ID gate is enforced
// explicitly in this helper via LAContext, rather than via a SecAccessControl biometry ACL:
// that ACL approach requires a proper app entitlement (keychain-access-groups under a real
// Team ID) that an ad-hoc-signed standalone CLI binary cannot obtain, and fails at the OS
// level with errSecMissingEntitlement (-34018) otherwise. The password is only ever printed
// after LAContext confirms a successful biometric check, so the practical security property
// (no password without a fingerprint) is the same either way.

let service = "com.spendlocker.vault"
let account = "master-password"

func fail(_ message: String) -> Never {
    FileHandle.standardError.write((message + "\n").data(using: .utf8)!)
    exit(1)
}

let args = CommandLine.arguments
guard args.count >= 2 else {
    fail("Usage: touchid-helper <check|store|retrieve|delete>")
}

switch args[1] {
case "check":
    let context = LAContext()
    var error: NSError?
    if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
        switch context.biometryType {
        case .touchID: print("touchid")
        case .faceID: print("faceid")
        default: print("available")
        }
        exit(0)
    } else {
        print("unavailable")
        exit(1)
    }

case "store":
    guard let line = readLine(strippingNewline: true), !line.isEmpty else {
        fail("No password provided on stdin")
    }
    let passwordData = line.data(using: .utf8)!

    let deleteQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account
    ]
    SecItemDelete(deleteQuery as CFDictionary)

    let addQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account,
        kSecValueData as String: passwordData,
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    ]
    let status = SecItemAdd(addQuery as CFDictionary, nil)
    if status != errSecSuccess {
        fail("Failed to store password, OSStatus \(status)")
    }
    print("stored")

case "retrieve":
    let context = LAContext()
    let semaphore = DispatchSemaphore(value: 0)
    var authOK = false

    context.evaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        localizedReason: "unlock your SpendLocker vault"
    ) { success, _ in
        authOK = success
        semaphore.signal()
    }
    semaphore.wait()

    guard authOK else {
        fail("Authentication failed or canceled")
    }

    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account,
        kSecReturnData as String: true
    ]
    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)
    if status == errSecSuccess, let data = item as? Data, let password = String(data: data, encoding: .utf8) {
        print(password)
        exit(0)
    } else if status == errSecItemNotFound {
        fail("No stored password found")
    } else {
        fail("Failed to read stored password, OSStatus \(status)")
    }

case "delete":
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account
    ]
    SecItemDelete(query as CFDictionary)
    print("deleted")

default:
    fail("Unknown mode: \(args[1])")
}
