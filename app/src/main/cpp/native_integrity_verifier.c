#include "native_integrity_verifier.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <signal.h>

#define ZIP_EOCD_MAGIC          0x06054b50
#define APK_SIG_BLOCK_MAGIC_STR "APK Sig Block 42"
#define SCHEME_V2_BLOCK_ID      0x7109871a
#define SCHEME_V3_BLOCK_ID      0xf05368c0

/* --- Minimal self-contained SHA-256 --- */
typedef struct {
    uint32_t state[8];
    uint64_t count;
    uint8_t buffer[64];
} SHA256_CTX;

#define ROR32(val, bits) (((val) >> (bits)) | ((val) << (32 - (bits))))
#define CH(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x) (ROR32(x, 2) ^ ROR32(x, 13) ^ ROR32(x, 22))
#define EP1(x) (ROR32(x, 6) ^ ROR32(x, 11) ^ ROR32(x, 25))
#define SIG0(x) (ROR32(x, 7) ^ ROR32(x, 18) ^ ((x) >> 3))
#define SIG1(x) (ROR32(x, 17) ^ ROR32(x, 19) ^ ((x) >> 10))

static const uint32_t K256[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

static void sha256_transform(SHA256_CTX *ctx, const uint8_t data[64]) {
    uint32_t a, b, c, d, e, f, g, h, t1, t2, m[64];
    for (int i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = ((uint32_t)data[j] << 24) | ((uint32_t)data[j+1] << 16) | ((uint32_t)data[j+2] << 8) | ((uint32_t)data[j+3]);
    for (int i = 16; i < 64; ++i)
        m[i] = SIG1(m[i - 2]) + m[i - 7] + SIG0(m[i - 15]) + m[i - 16];

    a = ctx->state[0]; b = ctx->state[1]; c = ctx->state[2]; d = ctx->state[3];
    e = ctx->state[4]; f = ctx->state[5]; g = ctx->state[6]; h = ctx->state[7];

    for (int i = 0; i < 64; ++i) {
        t1 = h + EP1(e) + CH(e, f, g) + K256[i] + m[i];
        t2 = EP0(a) + MAJ(a, b, c);
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    ctx->state[0] += a; ctx->state[1] += b; ctx->state[2] += c; ctx->state[3] += d;
    ctx->state[4] += e; ctx->state[5] += f; ctx->state[6] += g; ctx->state[7] += h;
}

static void sha256_init(SHA256_CTX *ctx) {
    ctx->state[0] = 0x6a09e667; ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372; ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f; ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab; ctx->state[7] = 0x5be0cd19;
    ctx->count = 0;
}

static void sha256_update(SHA256_CTX *ctx, const uint8_t *data, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        ctx->buffer[ctx->count % 64] = data[i];
        ctx->count++;
        if (ctx->count % 64 == 0)
            sha256_transform(ctx, ctx->buffer);
    }
}

static void sha256_final(SHA256_CTX *ctx, uint8_t hash[32]) {
    uint64_t total_bits = ctx->count * 8;
    ctx->buffer[ctx->count % 64] = 0x80;
    ctx->count++;
    if (ctx->count % 64 > 56) {
        while (ctx->count % 64 != 0) { ctx->buffer[ctx->count % 64] = 0; ctx->count++; }
        sha256_transform(ctx, ctx->buffer);
    }
    while (ctx->count % 64 < 56) { ctx->buffer[ctx->count % 64] = 0; ctx->count++; }
    for (int i = 7; i >= 0; --i) {
        ctx->buffer[ctx->count % 64] = (uint8_t)(total_bits >> (i * 8));
        ctx->count++;
    }
    sha256_transform(ctx, ctx->buffer);
    for (int i = 0; i < 8; ++i) {
        hash[i * 4] = (uint8_t)(ctx->state[i] >> 24);
        hash[i * 4 + 1] = (uint8_t)(ctx->state[i] >> 16);
        hash[i * 4 + 2] = (uint8_t)(ctx->state[i] >> 8);
        hash[i * 4 + 3] = (uint8_t)(ctx->state[i]);
    }
}

static void calculate_sha256(const uint8_t *data, size_t len, uint8_t out[32]) {
    SHA256_CTX ctx;
    sha256_init(&ctx);
    sha256_update(&ctx, data, len);
    sha256_final(&ctx, out);
}

/* --- Stage 1: Self-APK Discovery --- */
int find_self_apk_path(char *out_path, size_t max_len) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return INTEGRITY_ERR_SELF_NOT_FOUND;

    char line[1024];
    while (fgets(line, sizeof(line), fp)) {
        if ((strstr(line, "r-xp") || strstr(line, "r--p")) && strstr(line, ".apk")) {
            char *slash = strchr(line, '/');
            if (slash) {
                // Strip newline
                char *newline = strpbrk(slash, "\r\n");
                if (newline) *newline = '\0';
                if (access(slash, R_OK) == 0) {
                    strncpy(out_path, slash, max_len - 1);
                    out_path[max_len - 1] = '\0';
                    fclose(fp);
                    return INTEGRITY_SUCCESS;
                }
            }
        }
    }
    fclose(fp);
    return INTEGRITY_ERR_SELF_NOT_FOUND;
}

static uint32_t read_u32_le(const uint8_t *buf) {
    return (uint32_t)buf[0] | ((uint32_t)buf[1] << 8) | ((uint32_t)buf[2] << 16) | ((uint32_t)buf[3] << 24);
}

static uint64_t read_u64_le(const uint8_t *buf) {
    uint64_t val = 0;
    for (int i = 0; i < 8; ++i) val |= ((uint64_t)buf[i]) << (i * 8);
    return val;
}

/* Parse Scheme Block certificates */
static void parse_scheme_payload(const uint8_t *data, size_t len, ApkSignatureDetails *details) {
    if (len < 4) return;
    size_t offset = 0;
    uint32_t signers_len = read_u32_le(data + offset); offset += 4;
    size_t signers_end = (offset + signers_len > len) ? len : offset + signers_len;

    while (offset + 4 <= signers_end) {
        uint32_t signer_len = read_u32_le(data + offset); offset += 4;
        size_t signer_end = (offset + signer_len > signers_end) ? signers_end : offset + signer_len;
        if (offset + 4 > signer_end) break;

        uint32_t signed_data_len = read_u32_le(data + offset); offset += 4;
        size_t signed_data_end = (offset + signed_data_len > signer_end) ? signer_end : offset + signed_data_len;

        // Skip digests sequence
        if (offset + 4 > signed_data_end) { offset = signer_end; continue; }
        uint32_t digests_len = read_u32_le(data + offset); offset += 4;
        offset += digests_len;

        // Parse certificates sequence
        if (offset + 4 <= signed_data_end) {
            uint32_t certs_len = read_u32_le(data + offset); offset += 4;
            size_t certs_end = (offset + certs_len > signed_data_end) ? signed_data_end : offset + certs_len;

            while (offset + 4 <= certs_end && details->cert_count < MAX_CERTIFICATES) {
                uint32_t cert_len = read_u32_le(data + offset); offset += 4;
                if (offset + cert_len > certs_end) break;

                calculate_sha256(data + offset, cert_len, details->cert_digests[details->cert_count].hash);
                details->cert_count++;
                offset += cert_len;
            }
        }
        offset = signer_end;
    }
}

/* --- Stage 2 & 3: Binary Parsing of APK --- */
int parse_apk_signature_details(const char *apk_path, ApkSignatureDetails *out_details) {
    if (!apk_path || !out_details) return INTEGRITY_ERR_FILE_NOT_FOUND;
    memset(out_details, 0, sizeof(*out_details));

    int fd = open(apk_path, O_RDONLY);
    if (fd < 0) return INTEGRITY_ERR_FILE_NOT_FOUND;

    off_t file_len = lseek(fd, 0, SEEK_END);
    if (file_len <= 22) { close(fd); return INTEGRITY_ERR_EOCD_NOT_FOUND; }

    // 1. Locate ZIP EOCD
    size_t scan_len = (file_len < 65557) ? (size_t)file_len : 65557;
    off_t scan_start = file_len - scan_len;
    uint8_t *scan_buf = (uint8_t*)malloc(scan_len);
    if (!scan_buf) { close(fd); return INTEGRITY_ERR_EOCD_NOT_FOUND; }

    lseek(fd, scan_start, SEEK_SET);
    if (read(fd, scan_buf, scan_len) != (ssize_t)scan_len) {
        free(scan_buf); close(fd); return INTEGRITY_ERR_EOCD_NOT_FOUND;
    }

    uint32_t cd_offset = 0;
    bool found_eocd = false;
    for (ssize_t i = scan_len - 22; i >= 0; --i) {
        if (read_u32_le(scan_buf + i) == ZIP_EOCD_MAGIC) {
            cd_offset = read_u32_le(scan_buf + i + 16);
            found_eocd = true;
            break;
        }
    }
    free(scan_buf);
    if (!found_eocd || cd_offset < 24) { close(fd); return INTEGRITY_ERR_EOCD_NOT_FOUND; }

    // 2. Check Magic "APK Sig Block 42"
    lseek(fd, cd_offset - 16, SEEK_SET);
    char magic[16];
    if (read(fd, magic, 16) != 16 || memcmp(magic, APK_SIG_BLOCK_MAGIC_STR, 16) != 0) {
        close(fd); return INTEGRITY_ERR_SIG_BLOCK_NOT_FOUND;
    }

    // Read Block Size
    lseek(fd, cd_offset - 24, SEEK_SET);
    uint8_t size_buf[8];
    if (read(fd, size_buf, 8) != 8) { close(fd); return INTEGRITY_ERR_CORRUPT_BLOCK; }
    uint64_t block_size = read_u64_le(size_buf);
    if (block_size < 24 || block_size > cd_offset) { close(fd); return INTEGRITY_ERR_CORRUPT_BLOCK; }

    off_t block_start = cd_offset - 8 - block_size;
    lseek(fd, block_start, SEEK_SET);
    uint8_t leading_size_buf[8];
    if (read(fd, leading_size_buf, 8) != 8 || read_u64_le(leading_size_buf) != block_size) {
        close(fd); return INTEGRITY_ERR_CORRUPT_BLOCK;
    }

    // 3. Parse ID-Value pairs
    size_t pairs_len = (size_t)(block_size - 24);
    uint8_t *pairs_data = (uint8_t*)malloc(pairs_len);
    if (!pairs_data) { close(fd); return INTEGRITY_ERR_CORRUPT_BLOCK; }

    if (read(fd, pairs_data, pairs_len) != (ssize_t)pairs_len) {
        free(pairs_data); close(fd); return INTEGRITY_ERR_CORRUPT_BLOCK;
    }
    close(fd);

    size_t offset = 0;
    while (offset + 12 <= pairs_len) {
        uint64_t pair_len = read_u64_le(pairs_data + offset); offset += 8;
        if (pair_len < 4 || offset + pair_len > pairs_len) break;

        uint32_t pair_id = read_u32_le(pairs_data + offset);
        const uint8_t *val = pairs_data + offset + 4;
        size_t val_len = (size_t)(pair_len - 4);
        offset += pair_len;

        if (pair_id == SCHEME_V2_BLOCK_ID) {
            out_details->has_v2 = true;
            parse_scheme_payload(val, val_len, out_details);
        } else if (pair_id == SCHEME_V3_BLOCK_ID) {
            out_details->has_v3 = true;
            parse_scheme_payload(val, val_len, out_details);
        }
    }
    free(pairs_data);

    if (!out_details->has_v2 && !out_details->has_v3) {
        return INTEGRITY_ERR_NO_SCHEME;
    }

    return INTEGRITY_SUCCESS;
}

/* --- Stage 4 & 5: Validation and Enforcement --- */
int verify_apk_integrity(const char *apk_path, const Sha256Digest *expected_hashes, size_t expected_count) {
    ApkSignatureDetails details;
    int res = parse_apk_signature_details(apk_path, &details);
    if (res != INTEGRITY_SUCCESS) return res;

    for (size_t i = 0; i < details.cert_count; ++i) {
        for (size_t j = 0; j < expected_count; ++j) {
            if (memcmp(details.cert_digests[i].hash, expected_hashes[j].hash, SHA256_DIGEST_LENGTH) == 0) {
                return INTEGRITY_SUCCESS;
            }
        }
    }
    return INTEGRITY_ERR_SIGNATURE_MISMATCH;
}

int verify_self_integrity(const Sha256Digest *expected_hashes, size_t expected_count, bool terminate_on_failure) {
    char apk_path[1024];
    int path_res = find_self_apk_path(apk_path, sizeof(apk_path));
    if (path_res != INTEGRITY_SUCCESS) {
        if (terminate_on_failure) raise(SIGSEGV);
        return path_res;
    }

    int verify_res = verify_apk_integrity(apk_path, expected_hashes, expected_count);
    if (verify_res != INTEGRITY_SUCCESS && terminate_on_failure) {
        // Stage 5: Active Termination
        raise(SIGSEGV);
    }
    return verify_res;
}

#ifdef TEST_HARNESS
int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <path-to-apk>\n", argv[0]);
        return 1;
    }
    ApkSignatureDetails details;
    int res = parse_apk_signature_details(argv[1], &details);
    if (res != INTEGRITY_SUCCESS) {
        fprintf(stderr, "parse_apk_signature_details failed with error %d\n", res);
        return res;
    }
    printf("Native C Integrity Parser Results:\n");
    printf("  Scheme v2: %s\n", details.has_v2 ? "true" : "false");
    printf("  Scheme v3: %s\n", details.has_v3 ? "true" : "false");
    printf("  Certificates count: %zu\n", details.cert_count);
    for (size_t i = 0; i < details.cert_count; ++i) {
        printf("  Cert #%zu SHA-256: ", i + 1);
        for (int b = 0; b < 32; ++b) {
            printf("%02x", details.cert_digests[i].hash[b]);
        }
        printf("\n");
    }
    return 0;
}
#endif
