use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use log::{error, info};

pub mod manifest;
pub mod merger;
pub mod zip_writer;
pub mod integrity;

#[no_mangle]
pub extern "system" fn Java_app_pwhs_universalantisplit_engine_RustAntiSplitBridge_nativeInitLogger(
    _env: JNIEnv,
    _class: JClass,
) {
    #[cfg(target_os = "android")]
    {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("RustAntiSplit"),
        );
    }
    info!("RustAntiSplit Native Engine Initialized");
}

#[no_mangle]
pub extern "system" fn Java_app_pwhs_universalantisplit_engine_RustAntiSplitBridge_nativeGetEngineVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let version = "Rust Native NDK Core v0.2.0 (High-Performance Zero-Copy Engine)";
    let output = env
        .new_string(version)
        .expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_app_pwhs_universalantisplit_engine_RustAntiSplitBridge_nativeSanitizeManifest(
    env: JNIEnv,
    _class: JClass,
    input_bytes: JByteArray,
) -> jbyteArray {
    let bytes = match env.convert_byte_array(&input_bytes) {
        Ok(b) => b,
        Err(_) => return input_bytes.into_raw(),
    };

    let sanitized = manifest::sanitize_axml(&bytes);
    match env.byte_array_from_slice(&sanitized) {
        Ok(arr) => arr.into_raw(),
        Err(_) => input_bytes.into_raw(),
    }
}

#[no_mangle]
pub extern "system" fn Java_app_pwhs_universalantisplit_engine_RustAntiSplitBridge_nativeMergeSplits(
    mut env: JNIEnv,
    _class: JClass,
    base_apk_path: JString,
    split_paths: JObjectArray,
    output_path: JString,
) -> jboolean {
    let base_path: String = match env.get_string(&base_apk_path) {
        Ok(s) => s.into(),
        Err(e) => {
            error!("Failed to get base_apk_path: {:?}", e);
            return JNI_FALSE;
        }
    };

    let out_path: String = match env.get_string(&output_path) {
        Ok(s) => s.into(),
        Err(e) => {
            error!("Failed to get output_path: {:?}", e);
            return JNI_FALSE;
        }
    };

    let len = match env.get_array_length(&split_paths) {
        Ok(l) => l,
        Err(e) => {
            error!("Failed to get split_paths length: {:?}", e);
            return JNI_FALSE;
        }
    };

    let mut splits = Vec::new();
    for i in 0..len {
        let obj = match env.get_object_array_element(&split_paths, i) {
            Ok(o) => o,
            Err(_) => return JNI_FALSE,
        };
        let jstr = JString::from(obj);
        if let Ok(java_str) = env.get_string(&jstr) {
            let s: String = java_str.into();
            splits.push(s);
        };
    }

    info!(
        "Rust nativeMergeSplits starting: base={}, splits={}, output={}",
        base_path,
        splits.len(),
        out_path
    );

    let options = merger::MergeOptions::default();
    match merger::merge_apks(&base_path, &splits, &out_path, &options) {
        Ok(_) => {
            info!("Rust nativeMergeSplits completed successfully");
            JNI_TRUE
        }
        Err(e) => {
            error!("Rust nativeMergeSplits failed: {:?}", e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_pwhs_universalantisplit_engine_RustAntiSplitBridge_nativeVerifyApkIntegrity(
    mut env: JNIEnv,
    _class: JClass,
    apk_path: JString,
) -> jstring {
    let path: String = match env.get_string(&apk_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    match integrity::parse_apk_signatures(&path) {
        Ok(info) => {
            let cert_hashes: Vec<String> = info
                .certificates
                .iter()
                .map(|c| format!("\"{}\"", c.sha256_hex))
                .collect();
            let json = format!(
                r#"{{"success":true,"hasV2":{},"hasV3":{},"certificates":[{}]}}"#,
                info.has_v2,
                info.has_v3,
                cert_hashes.join(",")
            );
            match env.new_string(json) {
                Ok(js) => js.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(e) => {
            let json = format!(r#"{{"success":false,"error":"{}"}}"#, e);
            match env.new_string(json) {
                Ok(js) => js.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_pwhs_universalantisplit_engine_RustAntiSplitBridge_nativeFindSelfApkPath(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match integrity::find_self_apk_path() {
        Ok(path) => match env.new_string(path) {
            Ok(js) => js.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(_) => std::ptr::null_mut(),
    }
}
