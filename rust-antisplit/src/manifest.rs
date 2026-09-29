/// Binary Android XML (AXML) in-place sanitizer.
///
/// This sanitizer neutralizes split APK attributes by overwriting their string pool entries
/// with neutral replacements of the EXACT same length. This guarantees that all byte offsets,
/// string indices, and chunk sizes remain 100% valid without rewriting the entire XML tree.

const REPLACEMENTS: &[(&[u8], &[u8])] = &[
    (
        b"com.android.vending.splits.required",
        b"com.android.vending.splits.merged__",
    ),
    (b"com.android.vending.splits", b"com.android.vending.merged"),
    (b"requiredSplitTypes", b"__req_SplitTypes__"),
    (b"configForSplit", b"__cfgForSplit_"),
    (b"isFeatureSplit", b"__isFeatSplit_"),
    (b"isolatedSplits", b"__isolated_spt"),
    (b"splitTypes", b"__sptTypes"),
    (b"split", b"__spt"),
];

pub fn sanitize_axml(data: &[u8]) -> Vec<u8> {
    let mut buffer = data.to_vec();

    // Check minimum AXML header: 2 bytes type (0x0003), 2 bytes header size (0x0008)
    if buffer.len() < 8 {
        return buffer;
    }

    // Replace UTF-8 sequences
    for &(target, replacement) in REPLACEMENTS {
        debug_assert_eq!(target.len(), replacement.len());
        replace_all_occurrences(&mut buffer, target, replacement);
    }

    // Replace UTF-16LE sequences
    for &(target, replacement) in REPLACEMENTS {
        let utf16_target = to_utf16le(target);
        let utf16_replacement = to_utf16le(replacement);
        replace_all_occurrences(&mut buffer, &utf16_target, &utf16_replacement);
    }

    buffer
}

fn replace_all_occurrences(buffer: &mut [u8], target: &[u8], replacement: &[u8]) {
    if target.is_empty() || buffer.len() < target.len() {
        return;
    }

    let mut i = 0;
    while i + target.len() <= buffer.len() {
        if &buffer[i..i + target.len()] == target {
            buffer[i..i + target.len()].copy_from_slice(replacement);
            i += target.len();
        } else {
            i += 1;
        }
    }
}

fn to_utf16le(bytes: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(bytes.len() * 2);
    for &b in bytes {
        out.push(b);
        out.push(0x00);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_utf8_replacement() {
        let sample = b"dummy_split_attribute_test".to_vec();
        let sanitized = sanitize_axml(&sample);
        assert_eq!(sanitized, b"dummy___spt_attribute_test");
    }

    #[test]
    fn test_utf16le_replacement() {
        let original = to_utf16le(b"<manifest split=\"config.arm64\">");
        let sanitized = sanitize_axml(&original);
        let expected = to_utf16le(b"<manifest __spt=\"config.arm64\">");
        assert_eq!(sanitized, expected);
    }
}
