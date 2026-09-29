use std::collections::HashMap;

const RES_STRING_POOL_TYPE: u16 = 0x0001;
const RES_TABLE_TYPE: u16 = 0x0002;
const RES_TABLE_PACKAGE_TYPE: u16 = 0x0200;
const RES_TABLE_TYPE_TYPE: u16 = 0x0201;
const RES_TABLE_TYPE_SPEC_TYPE: u16 = 0x0202;
const RES_TABLE_LIBRARY_TYPE: u16 = 0x0203;

const TYPE_STRING: u8 = 0x03;
const ENTRY_FLAG_COMPLEX: u16 = 0x0001;
const NO_ENTRY: u32 = 0xFFFFFFFF;

#[inline]
fn read_u16(buf: &[u8], offset: usize) -> u16 {
    u16::from_le_bytes([buf[offset], buf[offset + 1]])
}

#[inline]
fn read_u32(buf: &[u8], offset: usize) -> u32 {
    u32::from_le_bytes([
        buf[offset],
        buf[offset + 1],
        buf[offset + 2],
        buf[offset + 3],
    ])
}

#[inline]
fn write_u32(buf: &mut [u8], offset: usize, val: u32) {
    buf[offset..offset + 4].copy_from_slice(&val.to_le_bytes());
}

/// Raw representation of an Android StringPool, maintaining raw bytes
/// to prevent panics or corruptions with CESU-8 / surrogate emoji encodings.
#[derive(Clone, Debug, Default)]
pub struct RawStringPool {
    pub flags: u32,
    pub strings: Vec<Vec<u8>>,
    pub styles: Vec<Vec<u8>>,
}

impl RawStringPool {
    pub fn parse(data: &[u8], offset: usize) -> Result<(Self, usize), String> {
        if offset + 28 > data.len() {
            return Err("StringPool offset out of bounds".to_string());
        }
        let chunk_size = read_u32(data, offset + 4) as usize;
        if offset + chunk_size > data.len() {
            return Err("StringPool chunk_size out of bounds".to_string());
        }
        let string_count = read_u32(data, offset + 8) as usize;
        let style_count = read_u32(data, offset + 12) as usize;
        let flags = read_u32(data, offset + 16);
        let strings_start = read_u32(data, offset + 20) as usize;
        let styles_start = read_u32(data, offset + 24) as usize;

        let mut string_offsets = Vec::with_capacity(string_count);
        for i in 0..string_count {
            let pos = offset + 28 + i * 4;
            if pos + 4 > data.len() {
                return Err("StringPool string offset out of bounds".to_string());
            }
            string_offsets.push(read_u32(data, pos) as usize);
        }

        let mut style_offsets = Vec::with_capacity(style_count);
        let style_offset_base = offset + 28 + string_count * 4;
        for i in 0..style_count {
            let pos = style_offset_base + i * 4;
            if pos + 4 > data.len() {
                return Err("StringPool style offset out of bounds".to_string());
            }
            style_offsets.push(read_u32(data, pos) as usize);
        }

        let strings_data_base = offset + strings_start;
        let mut strings = Vec::with_capacity(string_count);
        for i in 0..string_count {
            let start = strings_data_base + string_offsets[i];
            let end = if i + 1 < string_count {
                strings_data_base + string_offsets[i + 1]
            } else if style_count > 0 {
                offset + styles_start
            } else {
                offset + chunk_size
            };
            if start > data.len() || end > data.len() || start > end {
                return Err("StringPool string entry out of bounds".to_string());
            }
            strings.push(data[start..end].to_vec());
        }

        let mut styles = Vec::with_capacity(style_count);
        if style_count > 0 && styles_start > 0 {
            let styles_data_base = offset + styles_start;
            for i in 0..style_count {
                let start = styles_data_base + style_offsets[i];
                let end = if i + 1 < style_count {
                    styles_data_base + style_offsets[i + 1]
                } else {
                    offset + chunk_size
                };
                if start <= data.len() && end <= data.len() && start <= end {
                    styles.push(data[start..end].to_vec());
                }
            }
        }

        Ok((
            Self {
                flags,
                strings,
                styles,
            },
            chunk_size,
        ))
    }

    pub fn serialize(&self) -> Vec<u8> {
        let string_count = self.strings.len();
        let style_count = self.styles.len();
        let header_size = 28;

        let offsets_size = string_count * 4 + style_count * 4;
        let strings_start = header_size + offsets_size;

        let mut strings_buf = Vec::new();
        let mut string_offsets = Vec::with_capacity(string_count);
        for s in &self.strings {
            string_offsets.push(strings_buf.len() as u32);
            strings_buf.extend_from_slice(s);
        }

        while strings_buf.len() % 4 != 0 {
            strings_buf.push(0x00);
        }

        let styles_start = if style_count > 0 {
            strings_start + strings_buf.len()
        } else {
            0
        };

        let mut styles_buf = Vec::new();
        let mut style_offsets = Vec::with_capacity(style_count);
        for s in &self.styles {
            style_offsets.push(styles_buf.len() as u32);
            styles_buf.extend_from_slice(s);
        }
        while styles_buf.len() % 4 != 0 {
            styles_buf.push(0x00);
        }

        let total_size = strings_start + strings_buf.len() + styles_buf.len();

        let mut out = Vec::with_capacity(total_size);
        out.extend_from_slice(&RES_STRING_POOL_TYPE.to_le_bytes());
        out.extend_from_slice(&(header_size as u16).to_le_bytes());
        out.extend_from_slice(&(total_size as u32).to_le_bytes());
        out.extend_from_slice(&(string_count as u32).to_le_bytes());
        out.extend_from_slice(&(style_count as u32).to_le_bytes());
        out.extend_from_slice(&self.flags.to_le_bytes());
        out.extend_from_slice(&(strings_start as u32).to_le_bytes());
        out.extend_from_slice(&(styles_start as u32).to_le_bytes());

        for off in string_offsets {
            out.extend_from_slice(&off.to_le_bytes());
        }
        for off in style_offsets {
            out.extend_from_slice(&off.to_le_bytes());
        }
        out.extend_from_slice(&strings_buf);
        out.extend_from_slice(&styles_buf);

        out
    }

    /// Merges an incoming string pool, returning mapping of incoming_idx -> new_idx.
    pub fn merge(&mut self, incoming: &RawStringPool) -> HashMap<u32, u32> {
        let mut index_map = HashMap::with_capacity(incoming.strings.len());
        let mut existing_map: HashMap<Vec<u8>, u32> = HashMap::with_capacity(self.strings.len());
        for (i, s) in self.strings.iter().enumerate() {
            existing_map.entry(s.clone()).or_insert(i as u32);
        }

        for (incoming_idx, s) in incoming.strings.iter().enumerate() {
            if let Some(&exist_idx) = existing_map.get(s) {
                index_map.insert(incoming_idx as u32, exist_idx);
            } else {
                let new_idx = self.strings.len() as u32;
                self.strings.push(s.clone());
                existing_map.insert(s.clone(), new_idx);
                index_map.insert(incoming_idx as u32, new_idx);
            }
        }
        index_map
    }
}

fn remap_type_chunk(
    chunk_data: &mut [u8],
    key_map: &HashMap<u32, u32>,
    global_string_map: &HashMap<u32, u32>,
) {
    if chunk_data.len() < 20 {
        return;
    }
    let header_size = read_u16(chunk_data, 2) as usize;
    let flags = chunk_data[9];
    let is_sparse = (flags & 0x01) != 0;
    let is_offset16 = (flags & 0x02) != 0;
    let entry_count = read_u32(chunk_data, 12) as usize;
    let entries_start = read_u32(chunk_data, 16) as usize;

    if is_sparse {
        for i in 0..entry_count {
            let offset_pos = header_size + i * 4;
            if offset_pos + 4 <= chunk_data.len() {
                let entry_off = read_u16(chunk_data, offset_pos + 2) as usize * 4;
                let abs_off = entries_start + entry_off;
                if abs_off + 8 <= chunk_data.len() {
                    remap_single_entry(chunk_data, abs_off, key_map, global_string_map);
                }
            }
        }
    } else if is_offset16 {
        for i in 0..entry_count {
            let offset_pos = header_size + i * 2;
            if offset_pos + 2 <= chunk_data.len() {
                let off16 = read_u16(chunk_data, offset_pos);
                if off16 != 0xFFFF {
                    let abs_off = entries_start + (off16 as usize) * 4;
                    if abs_off + 8 <= chunk_data.len() {
                        remap_single_entry(chunk_data, abs_off, key_map, global_string_map);
                    }
                }
            }
        }
    } else {
        for i in 0..entry_count {
            let offset_pos = header_size + i * 4;
            if offset_pos + 4 <= chunk_data.len() {
                let off32 = read_u32(chunk_data, offset_pos);
                if off32 != NO_ENTRY {
                    let abs_off = entries_start + off32 as usize;
                    if abs_off + 8 <= chunk_data.len() {
                        remap_single_entry(chunk_data, abs_off, key_map, global_string_map);
                    }
                }
            }
        }
    }
}

fn remap_single_entry(
    data: &mut [u8],
    entry_off: usize,
    key_map: &HashMap<u32, u32>,
    global_string_map: &HashMap<u32, u32>,
) {
    if entry_off + 8 > data.len() {
        return;
    }
    let entry_size = read_u16(data, entry_off) as usize;
    let flags = read_u16(data, entry_off + 2);
    let key_idx = read_u32(data, entry_off + 4);

    if let Some(&new_key) = key_map.get(&key_idx) {
        write_u32(data, entry_off + 4, new_key);
    }

    let is_complex = (flags & ENTRY_FLAG_COMPLEX) != 0;
    if !is_complex {
        let val_off = entry_off + entry_size;
        if val_off + 8 <= data.len() {
            let data_type = data[val_off + 3];
            if data_type == TYPE_STRING {
                let str_idx = read_u32(data, val_off + 4);
                if let Some(&new_str) = global_string_map.get(&str_idx) {
                    write_u32(data, val_off + 4, new_str);
                }
            }
        }
    } else if entry_off + 16 <= data.len() {
        let count = read_u32(data, entry_off + 12) as usize;
        let mut map_item_off = entry_off + entry_size;
        for _ in 0..count {
            if map_item_off + 12 <= data.len() {
                let data_type = data[map_item_off + 7];
                if data_type == TYPE_STRING {
                    let str_idx = read_u32(data, map_item_off + 8);
                    if let Some(&new_str) = global_string_map.get(&str_idx) {
                        write_u32(data, map_item_off + 8, new_str);
                    }
                }
                map_item_off += 12;
            }
        }
    }
}

/// Merges a single split resources.arsc into the base resources.arsc
fn merge_single_arsc(base_data: &[u8], split_data: &[u8]) -> Result<Vec<u8>, String> {
    if base_data.len() < 12 || split_data.len() < 12 {
        return Err("Invalid ARSC file header".to_string());
    }
    if read_u16(base_data, 0) != RES_TABLE_TYPE || read_u16(split_data, 0) != RES_TABLE_TYPE {
        return Err("Not a RES_TABLE chunk".to_string());
    }

    // 1. Merge Global String Pool
    let (mut base_sp, base_sp_size) = RawStringPool::parse(base_data, 12)?;
    let (split_sp, split_sp_size) = RawStringPool::parse(split_data, 12)?;
    let global_str_map = base_sp.merge(&split_sp);
    let serialized_sp = base_sp.serialize();

    // 2. Parse split packages: pkg_id -> (key_pool, inner_chunks)
    let mut split_pkgs = HashMap::new();
    let mut split_pkg_offset = 12 + split_sp_size;
    while split_pkg_offset + 8 <= split_data.len() {
        let chunk_type = read_u16(split_data, split_pkg_offset);
        let chunk_size = read_u32(split_data, split_pkg_offset + 4) as usize;
        if chunk_size == 0 || split_pkg_offset + chunk_size > split_data.len() {
            break;
        }
        if chunk_type == RES_TABLE_PACKAGE_TYPE && chunk_size >= 288 {
            let pkg_id = read_u32(split_data, split_pkg_offset + 8);
            let pkg_hdr_size = read_u16(split_data, split_pkg_offset + 2) as usize;
            let key_str_off = read_u32(split_data, split_pkg_offset + 276) as usize;

            if let Ok((split_key_pool, _)) =
                RawStringPool::parse(split_data, split_pkg_offset + key_str_off)
            {
                let mut inner_chunks = Vec::new();
                let mut inner = split_pkg_offset + pkg_hdr_size;
                let pkg_end = split_pkg_offset + chunk_size;
                while inner + 8 <= pkg_end {
                    let inner_type = read_u16(split_data, inner);
                    let inner_size = read_u32(split_data, inner + 4) as usize;
                    if inner_size == 0 || inner + inner_size > pkg_end {
                        break;
                    }
                    if inner_type == RES_TABLE_TYPE_TYPE
                        || inner_type == RES_TABLE_TYPE_SPEC_TYPE
                        || inner_type == RES_TABLE_LIBRARY_TYPE
                    {
                        inner_chunks.push((inner_type, split_data[inner..inner + inner_size].to_vec()));
                    }
                    inner += inner_size;
                }
                split_pkgs.insert(pkg_id, (split_key_pool, inner_chunks));
            }
        }
        split_pkg_offset += chunk_size;
    }

    // 3. Process base packages and merge split packages
    let mut merged_packages: Vec<Vec<u8>> = Vec::new();
    let mut base_pkg_offset = 12 + base_sp_size;
    while base_pkg_offset + 8 <= base_data.len() {
        let chunk_type = read_u16(base_data, base_pkg_offset);
        let chunk_size = read_u32(base_data, base_pkg_offset + 4) as usize;
        if chunk_size == 0 || base_pkg_offset + chunk_size > base_data.len() {
            break;
        }
        if chunk_type == RES_TABLE_PACKAGE_TYPE && chunk_size >= 288 {
            let pkg_id = read_u32(base_data, base_pkg_offset + 8);
            let pkg_hdr_size = read_u16(base_data, base_pkg_offset + 2) as usize;
            let type_str_off = read_u32(base_data, base_pkg_offset + 268) as usize;
            let key_str_off = read_u32(base_data, base_pkg_offset + 276) as usize;

            let (type_pool, _) =
                RawStringPool::parse(base_data, base_pkg_offset + type_str_off)?;
            let (mut key_pool, _) =
                RawStringPool::parse(base_data, base_pkg_offset + key_str_off)?;

            let mut additional_chunks = Vec::new();
            if let Some((split_keys, split_chunks)) = split_pkgs.get(&pkg_id) {
                let key_map = key_pool.merge(split_keys);
                for (c_type, mut c_data) in split_chunks.clone() {
                    if c_type == RES_TABLE_TYPE_TYPE {
                        remap_type_chunk(&mut c_data, &key_map, &global_str_map);
                        additional_chunks.push(c_data);
                    } else if c_type == RES_TABLE_TYPE_SPEC_TYPE {
                        additional_chunks.push(c_data);
                    }
                }
            }

            let mut pkg_body = Vec::new();
            let type_sp_serialized = type_pool.serialize();
            let new_type_str_off = 288;
            pkg_body.extend_from_slice(&type_sp_serialized);

            let key_sp_serialized = key_pool.serialize();
            let new_key_str_off = 288 + pkg_body.len();
            pkg_body.extend_from_slice(&key_sp_serialized);

            let mut inner = base_pkg_offset + pkg_hdr_size;
            let pkg_end = base_pkg_offset + chunk_size;
            while inner + 8 <= pkg_end {
                let inner_size = read_u32(base_data, inner + 4) as usize;
                if inner_size == 0 || inner + inner_size > pkg_end {
                    break;
                }
                if inner != base_pkg_offset + type_str_off && inner != base_pkg_offset + key_str_off {
                    pkg_body.extend_from_slice(&base_data[inner..inner + inner_size]);
                }
                inner += inner_size;
            }

            for c in additional_chunks {
                pkg_body.extend_from_slice(&c);
            }

            let total_pkg_size = (288 + pkg_body.len()) as u32;
            let mut pkg_hdr = base_data[base_pkg_offset..base_pkg_offset + 288].to_vec();
            write_u32(&mut pkg_hdr, 4, total_pkg_size);
            write_u32(&mut pkg_hdr, 268, new_type_str_off as u32);
            write_u32(&mut pkg_hdr, 276, new_key_str_off as u32);
            write_u32(&mut pkg_hdr, 280, key_pool.strings.len() as u32);

            let mut full_pkg = Vec::with_capacity(total_pkg_size as usize);
            full_pkg.extend_from_slice(&pkg_hdr);
            full_pkg.extend_from_slice(&pkg_body);
            merged_packages.push(full_pkg);
        }
        base_pkg_offset += chunk_size;
    }

    // 4. Assemble root TableChunk
    let header_size = 12u16;
    let mut total_table_size = 12 + serialized_sp.len();
    for p in &merged_packages {
        total_table_size += p.len();
    }

    let mut out_arsc = Vec::with_capacity(total_table_size);
    out_arsc.extend_from_slice(&RES_TABLE_TYPE.to_le_bytes());
    out_arsc.extend_from_slice(&header_size.to_le_bytes());
    out_arsc.extend_from_slice(&(total_table_size as u32).to_le_bytes());
    out_arsc.extend_from_slice(&(merged_packages.len() as u32).to_le_bytes());
    out_arsc.extend_from_slice(&serialized_sp);
    for p in merged_packages {
        out_arsc.extend_from_slice(&p);
    }

    Ok(out_arsc)
}

/// Merges split ARSC buffers into the base ARSC buffer.
pub fn merge_arsc(base_data: &[u8], split_data_list: &[Vec<u8>]) -> Result<Vec<u8>, String> {
    if split_data_list.is_empty() {
        return Ok(base_data.to_vec());
    }
    let mut current = base_data.to_vec();
    for split in split_data_list {
        match merge_single_arsc(&current, split) {
            Ok(merged) => current = merged,
            Err(e) => {
                log::warn!("Failed to merge split ARSC: {e}");
            }
        }
    }
    Ok(current)
}
