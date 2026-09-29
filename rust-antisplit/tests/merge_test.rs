use rust_antisplit::merger::{merge_apks, MergeOptions};
use std::fs::File;
use std::path::Path;
use zip::ZipArchive;

#[test]
fn test_shopee_merge_and_resources() {
    let scratch_dir = "/Users/nqmgaming/.gemini/antigravity-ide/brain/dc52b54a-e291-4402-8480-605d2a955fa1/scratch/shopee_apks";
    let base_apk = format!("{}/base.apk", scratch_dir);
    let xxhdpi_apk = format!("{}/split_config.xxhdpi.apk", scratch_dir);
    let arm64_apk = format!("{}/split_config.arm64_v8a.apk", scratch_dir);

    if !Path::new(&base_apk).exists() || !Path::new(&xxhdpi_apk).exists() {
        println!("Shopee APKs not present, skipping integration test");
        return;
    }

    let out_apk = format!("{}/merged_shopee_rust.apk", scratch_dir);
    if Path::new(&out_apk).exists() {
        let _ = std::fs::remove_file(&out_apk);
    }

    let result = merge_apks(&base_apk, &[&arm64_apk, &xxhdpi_apk], &out_apk, &MergeOptions::default());
    assert!(result.is_ok(), "Merge failed: {:?}", result.err());

    // Verify output APK
    let file = File::open(&out_apk).expect("Failed to open merged APK");
    let mut zip = ZipArchive::new(file).expect("Failed to parse merged APK");

    // resources.arsc must exist
    let arsc_size = {
        let arsc_entry = zip.by_name("resources.arsc").expect("resources.arsc not found");
        arsc_entry.size()
    };
    assert!(arsc_size > 3_800_000, "resources.arsc too small, was {}", arsc_size);

    // Verify lib/arm64-v8a exists
    let has_native_libs = (0..zip.len()).any(|i| {
        zip.by_index(i).map(|e| e.name().starts_with("lib/arm64-v8a/")).unwrap_or(false)
    });
    assert!(has_native_libs, "Merged APK missing native libs");

    // Verify drawable-xxhdpi exists
    let has_xxhdpi_drawables = (0..zip.len()).any(|i| {
        zip.by_index(i).map(|e| e.name().starts_with("res/drawable-xxhdpi")).unwrap_or(false)
    });
    assert!(has_xxhdpi_drawables, "Merged APK missing xxhdpi drawables");
}
