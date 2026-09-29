use std::io::{self, Seek, SeekFrom, Write};
use byteorder::{LittleEndian, WriteBytesExt};

pub struct AlignedZipWriter<W: Write + Seek> {
    writer: W,
    central_directory: Vec<CentralDirectoryHeader>,
}

struct CentralDirectoryHeader {
    file_name: String,
    header_offset: u64,
    compressed_size: u32,
    uncompressed_size: u32,
    crc32: u32,
    compression_method: u16,
    flags: u16,
    mod_time: u16,
    mod_date: u16,
}

impl<W: Write + Seek> AlignedZipWriter<W> {
    pub fn new(writer: W) -> Self {
        Self {
            writer,
            central_directory: Vec::new(),
        }
    }

    /// Determines the alignment requirement for a file:
    /// - `.so` -> 16384 bytes (16KB)
    /// - all other stored entries -> 4 bytes (Android zipalign 4 requirement)
    fn get_alignment(name: &str) -> usize {
        if name.ends_with(".so") {
            16384
        } else {
            4
        }
    }

    /// Writes raw uncompressed bytes with proper alignment for `.so` and `resources.arsc`.
    pub fn write_stored_entry(&mut self, name: &str, data: &[u8], crc32: u32) -> io::Result<()> {
        let align = Self::get_alignment(name);
        self.write_entry_internal(name, data, crc32, 0, align)
    }

    /// Writes pre-compressed (DEFLATE) data directly (e.g. copied from existing APK).
    pub fn write_raw_entry(
        &mut self,
        name: &str,
        raw_data: &[u8],
        crc32: u32,
        uncompressed_size: u32,
        compression_method: u16,
        align: usize,
    ) -> io::Result<()> {
        self.write_entry_raw_internal(name, raw_data, crc32, uncompressed_size, compression_method, align)
    }

    fn write_entry_internal(
        &mut self,
        name: &str,
        data: &[u8],
        crc32: u32,
        method: u16,
        align: usize,
    ) -> io::Result<()> {
        let size = data.len() as u32;
        self.write_entry_raw_internal(name, data, crc32, size, method, align)
    }

    fn write_entry_raw_internal(
        &mut self,
        name: &str,
        data: &[u8],
        crc32: u32,
        uncompressed_size: u32,
        method: u16,
        align: usize,
    ) -> io::Result<()> {
        let header_offset = self.writer.seek(SeekFrom::Current(0))?;
        let name_bytes = name.as_bytes();
        let name_len = name_bytes.len();

        let base_header_size = 30 + name_len;
        let unaligned_data_offset = (header_offset as usize) + base_header_size;

        let padding = if align > 1 {
            (align - (unaligned_data_offset % align)) % align
        } else {
            0
        };

        let extra_field = if padding > 0 {
            // Android zipalign convention: extra field id 0xd935 or null padding
            // We use standard 4-byte header [tag: u16, len: u16] if padding >= 4
            let mut extra = vec![0u8; padding];
            if padding >= 4 {
                extra[0] = 0xd9;
                extra[1] = 0x35;
                let data_len = (padding - 4) as u16;
                extra[2] = (data_len & 0xFF) as u8;
                extra[3] = ((data_len >> 8) & 0xFF) as u8;
            }
            extra
        } else {
            Vec::new()
        };

        // Write Local File Header
        self.writer.write_u32::<LittleEndian>(0x04034b50)?; // Local header signature
        self.writer.write_u16::<LittleEndian>(20)?;         // Version needed: 2.0
        self.writer.write_u16::<LittleEndian>(0)?;          // General purpose bit flag
        self.writer.write_u16::<LittleEndian>(method)?;     // Compression method
        self.writer.write_u16::<LittleEndian>(0)?;          // Last mod time
        self.writer.write_u16::<LittleEndian>(0)?;          // Last mod date
        self.writer.write_u32::<LittleEndian>(crc32)?;      // CRC-32
        self.writer.write_u32::<LittleEndian>(data.len() as u32)?; // Compressed size
        self.writer.write_u32::<LittleEndian>(uncompressed_size)?; // Uncompressed size
        self.writer.write_u16::<LittleEndian>(name_len as u16)?;    // File name length
        self.writer.write_u16::<LittleEndian>(extra_field.len() as u16)?; // Extra field length
        self.writer.write_all(name_bytes)?;
        if !extra_field.is_empty() {
            self.writer.write_all(&extra_field)?;
        }

        // Write file payload
        self.writer.write_all(data)?;

        // Record central directory header
        self.central_directory.push(CentralDirectoryHeader {
            file_name: name.to_string(),
            header_offset,
            compressed_size: data.len() as u32,
            uncompressed_size,
            crc32,
            compression_method: method,
            flags: 0,
            mod_time: 0,
            mod_date: 0,
        });

        Ok(())
    }

    /// Finalizes the archive by writing the Central Directory and End of Central Directory record.
    pub fn finish(mut self) -> io::Result<()> {
        let cd_offset = self.writer.seek(SeekFrom::Current(0))?;

        for entry in &self.central_directory {
            let name_bytes = entry.file_name.as_bytes();
            self.writer.write_u32::<LittleEndian>(0x02014b50)?; // CD signature
            self.writer.write_u16::<LittleEndian>(20)?;         // Version made by
            self.writer.write_u16::<LittleEndian>(20)?;         // Version needed
            self.writer.write_u16::<LittleEndian>(entry.flags)?;
            self.writer.write_u16::<LittleEndian>(entry.compression_method)?;
            self.writer.write_u16::<LittleEndian>(entry.mod_time)?;
            self.writer.write_u16::<LittleEndian>(entry.mod_date)?;
            self.writer.write_u32::<LittleEndian>(entry.crc32)?;
            self.writer.write_u32::<LittleEndian>(entry.compressed_size)?;
            self.writer.write_u32::<LittleEndian>(entry.uncompressed_size)?;
            self.writer.write_u16::<LittleEndian>(name_bytes.len() as u16)?;
            self.writer.write_u16::<LittleEndian>(0)?;          // Extra field length
            self.writer.write_u16::<LittleEndian>(0)?;          // File comment length
            self.writer.write_u16::<LittleEndian>(0)?;          // Disk number start
            self.writer.write_u16::<LittleEndian>(0)?;          // Internal attributes
            self.writer.write_u32::<LittleEndian>(0)?;          // External attributes
            self.writer.write_u32::<LittleEndian>(entry.header_offset as u32)?;
            self.writer.write_all(name_bytes)?;
        }

        let cd_end = self.writer.seek(SeekFrom::Current(0))?;
        let cd_size = cd_end - cd_offset;
        let entry_count = self.central_directory.len() as u16;

        // End of Central Directory Record (EOCD)
        self.writer.write_u32::<LittleEndian>(0x06054b50)?; // EOCD signature
        self.writer.write_u16::<LittleEndian>(0)?;          // Number of this disk
        self.writer.write_u16::<LittleEndian>(0)?;          // Disk where CD starts
        self.writer.write_u16::<LittleEndian>(entry_count)?; // Number of CD records on this disk
        self.writer.write_u16::<LittleEndian>(entry_count)?; // Total number of CD records
        self.writer.write_u32::<LittleEndian>(cd_size as u32)?; // Size of CD
        self.writer.write_u32::<LittleEndian>(cd_offset as u32)?; // Offset of CD
        self.writer.write_u16::<LittleEndian>(0)?;          // Comment length
        self.writer.flush()?;

        Ok(())
    }
}
