use std::collections::HashSet;
use std::fs::File;
use std::io::{self, BufReader, BufWriter, Read};
use std::path::Path;
use zip::ZipArchive;

use crate::manifest;
use crate::zip_writer::AlignedZipWriter;

#[derive(thiserror::Error, Debug)]
pub enum MergeError {
    #[error("I/O error: {0}")]
    Io(#[from] io::Error),
    #[error("Zip error: {0}")]
    Zip(#[from] zip::result::ZipError),
    #[error("Base APK not found: {0}")]
    BaseNotFound(String),
}

pub struct MergeOptions {
    pub align_16kb: bool,
}

impl Default for MergeOptions {
    fn default() -> Self {
        Self { align_16kb: true }
    }
}

pub fn merge_apks<P: AsRef<Path>, S: AsRef<Path>>(
    base_apk_path: P,
    split_apk_paths: &[S],
    output_path: P,
    _options: &MergeOptions,
) -> Result<(), MergeError> {
    let base_file = File::open(&base_apk_path)
        .map_err(|_| MergeError::BaseNotFound(base_apk_path.as_ref().to_string_lossy().to_string()))?;
    let mut base_zip = ZipArchive::new(BufReader::new(base_file))?;

    let out_file = File::create(&output_path)?;
    let mut writer = AlignedZipWriter::new(BufWriter::with_capacity(512 * 1024, out_file));

    let mut existing_entries = HashSet::new();
    let mut max_dex = 1;

    // Step 1: Process base.apk
    for i in 0..base_zip.len() {
        let mut entry = base_zip.by_index(i)?;
        let name = entry.name().to_string();

        if is_signature_file(&name) {
            continue;
        }

        let mut data = Vec::with_capacity(entry.size() as usize);
        entry.read_to_end(&mut data)?;

        if name == "AndroidManifest.xml" {
            let sanitized = manifest::sanitize_axml(&data);
            let crc = crc32fast::hash(&sanitized);
            writer.write_stored_entry(&name, &sanitized, crc)?;
            existing_entries.insert(name);
            continue;
        }

        if let Some(dex_num) = parse_dex_index(&name) {
            if dex_num > max_dex {
                max_dex = dex_num;
            }
        }

        let crc = entry.crc32();
        if name.ends_with(".so") || name == "resources.arsc" {
            writer.write_stored_entry(&name, &data, crc)?;
        } else {
            writer.write_stored_entry(&name, &data, crc)?;
        }
        existing_entries.insert(name);
    }

    // Step 2: Merge each split APK
    let mut next_dex = max_dex;
    for split_path in split_apk_paths {
        if !split_path.as_ref().exists() {
            continue;
        }

        let split_file = match File::open(split_path) {
            Ok(f) => f,
            Err(_) => continue,
        };
        let mut split_zip = match ZipArchive::new(BufReader::new(split_file)) {
            Ok(z) => z,
            Err(_) => continue,
        };

        for i in 0..split_zip.len() {
            let mut entry = match split_zip.by_index(i) {
                Ok(e) => e,
                Err(_) => continue,
            };
            let name = entry.name().to_string();

            if is_signature_file(&name) || name == "AndroidManifest.xml" || name == "resources.arsc" {
                continue;
            }

            let mut data = Vec::with_capacity(entry.size() as usize);
            if entry.read_to_end(&mut data).is_err() {
                continue;
            }

            if parse_dex_index(&name).is_some() {
                next_dex += 1;
                let renumbered_name = format!("classes{}.dex", next_dex);
                let crc = crc32fast::hash(&data);
                writer.write_stored_entry(&renumbered_name, &data, crc)?;
                existing_entries.insert(renumbered_name);
                continue;
            }

            if !existing_entries.contains(&name) {
                let crc = entry.crc32();
                writer.write_stored_entry(&name, &data, crc)?;
                existing_entries.insert(name);
            }
        }
    }

    writer.finish()?;
    Ok(())
}

fn is_signature_file(name: &str) -> bool {
    if !name.starts_with("META-INF/") {
        return false;
    }
    let upper = name.to_ascii_uppercase();
    upper.ends_with(".SF")
        || upper.ends_with(".RSA")
        || upper.ends_with(".DSA")
        || upper.ends_with(".EC")
        || upper == "META-INF/MANIFEST.MF"
}

fn parse_dex_index(name: &str) -> Option<u32> {
    if name == "classes.dex" {
        return Some(1);
    }
    if name.starts_with("classes") && name.ends_with(".dex") {
        let num_part = &name[7..name.len() - 4];
        return num_part.parse::<u32>().ok();
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dex_parsing() {
        assert_eq!(parse_dex_index("classes.dex"), Some(1));
        assert_eq!(parse_dex_index("classes2.dex"), Some(2));
        assert_eq!(parse_dex_index("classes15.dex"), Some(15));
        assert_eq!(parse_dex_index("not_a_dex.dex"), None);
    }

    #[test]
    fn test_signature_detection() {
        assert!(is_signature_file("META-INF/CERT.RSA"));
        assert!(is_signature_file("META-INF/CERT.SF"));
        assert!(is_signature_file("META-INF/MANIFEST.MF"));
        assert!(!is_signature_file("assets/META-INF/fake.RSA"));
        assert!(!is_signature_file("AndroidManifest.xml"));
    }

    #[test]
    fn test_merge_real_files() {
        let base = "../scratch/lienquan/base.apk";
        if !std::path::Path::new(base).exists() {
            return;
        }
        let splits = vec![
            "../scratch/lienquan/split_config.arm64_v8a.apk".to_string(),
            "../scratch/lienquan/split_assetpack.apk".to_string(),
        ];
        let out = "../scratch/rust_merged_test.apk";
        let options = MergeOptions::default();
        let start = std::time::Instant::now();
        let res = merge_apks(base, &splits, out, &options);
        println!("Rust merge took: {:?}", start.elapsed());
        assert!(res.is_ok());
    }
}

