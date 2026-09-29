use std::fs::File;
use std::io::{BufRead, BufReader, Read, Seek, SeekFrom};
use std::path::Path;

const ZIP_EOCD_MAGIC: u32 = 0x06054b50;
const APK_SIG_BLOCK_MAGIC: &[u8; 16] = b"APK Sig Block 42";
const SCHEME_V2_BLOCK_ID: u32 = 0x7109871a;
const SCHEME_V3_BLOCK_ID: u32 = 0xf05368c0;

#[derive(Debug, thiserror::Error)]
pub enum IntegrityError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("ZIP End of Central Directory (EOCD) not found")]
    EocdNotFound,
    #[error("APK Signing Block not found or invalid magic")]
    SigningBlockNotFound,
    #[error("Corrupt APK Signing Block header or size mismatch")]
    CorruptSigningBlock,
    #[error("No supported APK signature scheme found (v2 or v3)")]
    NoSignatureSchemeFound,
    #[error("Self APK path could not be resolved from /proc/self/maps")]
    SelfApkNotFound,
    #[error("Payload parsing error: {0}")]
    ParseError(String),
}

#[derive(Debug, Clone)]
pub struct CertificateInfo {
    pub raw_der: Vec<u8>,
    pub sha256_hex: String,
}

#[derive(Debug, Clone, Default)]
pub struct ApkSignatureInfo {
    pub has_v2: bool,
    pub has_v3: bool,
    pub certificates: Vec<CertificateInfo>,
}

/// Discovers current process's APK path by scanning /proc/self/maps
pub fn find_self_apk_path() -> Result<String, IntegrityError> {
    let file = File::open("/proc/self/maps").map_err(|_| IntegrityError::SelfApkNotFound)?;
    let reader = BufReader::new(file);

    for line in reader.lines() {
        let line = match line {
            Ok(l) => l,
            Err(_) => continue,
        };

        // Looking for lines containing an .apk path with read permission (r-xp or r--p)
        if (line.contains("r-xp") || line.contains("r--p")) && line.contains(".apk") {
            if let Some(path_start) = line.find('/') {
                let candidate = line[path_start..].trim();
                if candidate.ends_with(".apk") && Path::new(candidate).exists() {
                    return Ok(candidate.to_string());
                }
            }
        }
    }

    Err(IntegrityError::SelfApkNotFound)
}

/// Parses APK binary structure directly to extract APK Signature Scheme v2/v3 certificates
pub fn parse_apk_signatures<P: AsRef<Path>>(apk_path: P) -> Result<ApkSignatureInfo, IntegrityError> {
    let mut file = File::open(apk_path)?;
    let file_len = file.seek(SeekFrom::End(0))?;

    // 1. Locate ZIP EOCD
    let cd_offset = locate_central_directory_offset(&mut file, file_len)?;

    // 2. Locate and parse APK Signing Block
    if cd_offset < 24 {
        return Err(IntegrityError::SigningBlockNotFound);
    }

    // Read 16-byte magic directly preceding Central Directory
    file.seek(SeekFrom::Start(cd_offset - 16))?;
    let mut magic_buf = [0u8; 16];
    file.read_exact(&mut magic_buf)?;
    if &magic_buf != APK_SIG_BLOCK_MAGIC {
        return Err(IntegrityError::SigningBlockNotFound);
    }

    // Read 8-byte block size preceding the magic
    file.seek(SeekFrom::Start(cd_offset - 24))?;
    let mut size_buf = [0u8; 8];
    file.read_exact(&mut size_buf)?;
    let block_size = u64::from_le_bytes(size_buf);

    if block_size < 24 || block_size > cd_offset {
        return Err(IntegrityError::CorruptSigningBlock);
    }

    let block_start = cd_offset - 8 - block_size;
    file.seek(SeekFrom::Start(block_start))?;
    let mut leading_size_buf = [0u8; 8];
    file.read_exact(&mut leading_size_buf)?;
    if u64::from_le_bytes(leading_size_buf) != block_size {
        return Err(IntegrityError::CorruptSigningBlock);
    }

    // Read all ID-Value pairs in the block
    let pairs_len = (block_size - 24) as usize;
    let mut pairs_data = vec![0u8; pairs_len];
    file.read_exact(&mut pairs_data)?;

    let mut info = ApkSignatureInfo::default();
    let mut offset = 0;

    while offset + 12 <= pairs_data.len() {
        let pair_len = u64::from_le_bytes(pairs_data[offset..offset + 8].try_into().unwrap()) as usize;
        offset += 8;

        if pair_len < 4 || offset + pair_len > pairs_data.len() {
            break;
        }

        let pair_id = u32::from_le_bytes(pairs_data[offset..offset + 4].try_into().unwrap());
        let val_start = offset + 4;
        let val_end = offset + pair_len;
        let pair_value = &pairs_data[val_start..val_end];
        offset += pair_len;

        match pair_id {
            SCHEME_V2_BLOCK_ID => {
                info.has_v2 = true;
                parse_scheme_block(pair_value, &mut info.certificates)?;
            }
            SCHEME_V3_BLOCK_ID => {
                info.has_v3 = true;
                parse_scheme_block(pair_value, &mut info.certificates)?;
            }
            _ => {}
        }
    }

    if !info.has_v2 && !info.has_v3 {
        return Err(IntegrityError::NoSignatureSchemeFound);
    }

    Ok(info)
}

/// Locates the Central Directory offset by scanning backward for the ZIP EOCD marker
fn locate_central_directory_offset(file: &mut File, file_len: u64) -> Result<u64, IntegrityError> {
    const MIN_EOCD_SIZE: usize = 22;
    const MAX_COMMENT_SIZE: usize = 65535;
    let max_read = (MIN_EOCD_SIZE + MAX_COMMENT_SIZE).min(file_len as usize);

    let search_start = file_len - max_read as u64;
    file.seek(SeekFrom::Start(search_start))?;
    let mut buf = vec![0u8; max_read];
    file.read_exact(&mut buf)?;

    // Scan backward for 0x06054b50
    for i in (0..=buf.len() - MIN_EOCD_SIZE).rev() {
        let sig = u32::from_le_bytes(buf[i..i + 4].try_into().unwrap());
        if sig == ZIP_EOCD_MAGIC {
            let cd_offset = u32::from_le_bytes(buf[i + 16..i + 20].try_into().unwrap()) as u64;
            return Ok(cd_offset);
        }
    }

    Err(IntegrityError::EocdNotFound)
}

/// Parses an APK Signature Scheme v2 or v3 block and extracts X.509 DER certificates
fn parse_scheme_block(data: &[u8], certs: &mut Vec<CertificateInfo>) -> Result<(), IntegrityError> {
    let mut offset = 0;
    if data.len() < 4 {
        return Ok(());
    }

    let signers_len = read_u32_le(data, &mut offset)? as usize;
    let signers_end = (offset + signers_len).min(data.len());

    while offset + 4 <= signers_end {
        let signer_len = read_u32_le(data, &mut offset)? as usize;
        let signer_end = (offset + signer_len).min(signers_end);

        if offset + 4 > signer_end {
            break;
        }

        // Signed data block
        let signed_data_len = read_u32_le(data, &mut offset)? as usize;
        let signed_data_end = (offset + signed_data_len).min(signer_end);

        // Skip digests sequence
        if offset + 4 > signed_data_end {
            offset = signer_end;
            continue;
        }
        let digests_len = read_u32_le(data, &mut offset)? as usize;
        offset = (offset + digests_len).min(signed_data_end);

        // Certificates sequence
        if offset + 4 <= signed_data_end {
            let certs_seq_len = read_u32_le(data, &mut offset)? as usize;
            let certs_seq_end = (offset + certs_seq_len).min(signed_data_end);

            while offset + 4 <= certs_seq_end {
                let cert_len = read_u32_le(data, &mut offset)? as usize;
                if offset + cert_len > certs_seq_end {
                    break;
                }

                let der_bytes = data[offset..offset + cert_len].to_vec();
                offset += cert_len;

                let digest = sha256(&der_bytes);
                let sha256_hex = hex_encode(&digest);

                if !certs.iter().any(|c| c.sha256_hex == sha256_hex) {
                    certs.push(CertificateInfo {
                        raw_der: der_bytes,
                        sha256_hex,
                    });
                }
            }
        }

        offset = signer_end;
    }

    Ok(())
}

fn read_u32_le(data: &[u8], offset: &mut usize) -> Result<u32, IntegrityError> {
    if *offset + 4 > data.len() {
        return Err(IntegrityError::ParseError("Unexpected end of data".into()));
    }
    let val = u32::from_le_bytes(data[*offset..*offset + 4].try_into().unwrap());
    *offset += 4;
    Ok(val)
}

/// Self-contained SHA-256 implementation
pub fn sha256(data: &[u8]) -> [u8; 32] {
    let mut h: [u32; 8] = [
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    ];

    let k: [u32; 64] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ];

    let bit_len = (data.len() as u64) * 8;
    let mut msg = data.to_vec();
    msg.push(0x80);
    while (msg.len() + 8) % 64 != 0 {
        msg.push(0x00);
    }
    msg.extend_from_slice(&bit_len.to_be_bytes());

    for chunk in msg.chunks_exact(64) {
        let mut w = [0u32; 64];
        for i in 0..16 {
            w[i] = u32::from_be_bytes(chunk[i * 4..i * 4 + 4].try_into().unwrap());
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16].wrapping_add(s0).wrapping_add(w[i - 7]).wrapping_add(s1);
        }

        let mut a = h[0];
        let mut b = h[1];
        let mut c = h[2];
        let mut d = h[3];
        let mut e = h[4];
        let mut f = h[5];
        let mut g = h[6];
        let mut h_var = h[7];

        for i in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = h_var
                .wrapping_add(s1)
                .wrapping_add(ch)
                .wrapping_add(k[i])
                .wrapping_add(w[i]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            h_var = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }

        h[0] = h[0].wrapping_add(a);
        h[1] = h[1].wrapping_add(b);
        h[2] = h[2].wrapping_add(c);
        h[3] = h[3].wrapping_add(d);
        h[4] = h[4].wrapping_add(e);
        h[5] = h[5].wrapping_add(f);
        h[6] = h[6].wrapping_add(g);
        h[7] = h[7].wrapping_add(h_var);
    }

    let mut out = [0u8; 32];
    for (i, val) in h.iter().enumerate() {
        out[i * 4..i * 4 + 4].copy_from_slice(&val.to_be_bytes());
    }
    out
}

pub fn hex_encode(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push_str(&format!("{:02x}", b));
    }
    s
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha256_empty() {
        let empty_hash = sha256(b"");
        assert_eq!(
            hex_encode(&empty_hash),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
    }

    #[test]
    fn test_sha256_abc() {
        let abc_hash = sha256(b"abc");
        assert_eq!(
            hex_encode(&abc_hash),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    #[test]
    fn test_parse_real_apk() {
        let path = "../scratch/lienquan/base.apk";
        if std::path::Path::new(path).exists() {
            let res = parse_apk_signatures(path);
            assert!(res.is_ok(), "Failed to parse APK: {:?}", res.err());
            let info = res.unwrap();
            println!("Parsed APK info: v2={}, v3={}, certs={}", info.has_v2, info.has_v3, info.certificates.len());
            assert!(!info.certificates.is_empty(), "Expected at least 1 certificate");
            println!("Certificate SHA-256: {}", info.certificates[0].sha256_hex);
        }
    }
}
