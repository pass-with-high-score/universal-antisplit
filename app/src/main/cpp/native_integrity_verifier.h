#ifndef NATIVE_INTEGRITY_VERIFIER_H
#define NATIVE_INTEGRITY_VERIFIER_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define INTEGRITY_SUCCESS                   0
#define INTEGRITY_ERR_FILE_NOT_FOUND       -1
#define INTEGRITY_ERR_EOCD_NOT_FOUND       -2
#define INTEGRITY_ERR_SIG_BLOCK_NOT_FOUND  -3
#define INTEGRITY_ERR_CORRUPT_BLOCK        -4
#define INTEGRITY_ERR_NO_SCHEME            -5
#define INTEGRITY_ERR_SIGNATURE_MISMATCH   -6
#define INTEGRITY_ERR_SELF_NOT_FOUND       -7

#define SHA256_DIGEST_LENGTH               32
#define MAX_CERTIFICATES                   16

typedef struct {
    uint8_t hash[SHA256_DIGEST_LENGTH];
} Sha256Digest;

typedef struct {
    bool has_v2;
    bool has_v3;
    size_t cert_count;
    Sha256Digest cert_digests[MAX_CERTIFICATES];
} ApkSignatureDetails;

/**
 * Stage 1: Self-APK Discovery
 * Discovers the absolute path of the running APK from /proc/self/maps.
 */
int find_self_apk_path(char *out_path, size_t max_len);

/**
 * Stage 2 & 3: Binary Parsing of APK Signature Scheme v2/v3
 * Opens the APK file, traverses ZIP EOCD and APK Signing Block,
 * and extracts the SHA-256 certificate digests.
 */
int parse_apk_signature_details(const char *apk_path, ApkSignatureDetails *out_details);

/**
 * Stage 4: Validation & Attestation
 * Verifies whether the APK's certificates match any of the expected SHA-256 digests.
 */
int verify_apk_integrity(const char *apk_path, const Sha256Digest *expected_hashes, size_t expected_count);

/**
 * Stage 5: Self Enforcement
 * Discovers self APK, checks signature against trusted digests.
 * If signature check fails, optionally triggers active termination.
 */
int verify_self_integrity(const Sha256Digest *expected_hashes, size_t expected_count, bool terminate_on_failure);

#ifdef __cplusplus
}
#endif

#endif // NATIVE_INTEGRITY_VERIFIER_H
