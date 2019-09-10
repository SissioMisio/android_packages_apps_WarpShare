package org.mokee.warpshare.airdrop;

import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.mokee.warpshare.ResolvedUri;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;

import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IRGRP;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IROTH;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IRUSR;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_ISREG;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IWUSR;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.FORMAT_OLD_ASCII;

class AirDropArchiveUtil {

    static void pack(List<ResolvedUri> uris, OutputStream output) throws IOException {
        try (final GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(output);
             final CpioArchiveOutputStream cpio = new CpioArchiveOutputStream(gzip, FORMAT_OLD_ASCII)) {
            for (ResolvedUri uri : uris) {
                final CpioArchiveEntry entry = new CpioArchiveEntry(FORMAT_OLD_ASCII, uri.path());
                entry.setMode(C_ISREG | C_IRUSR | C_IWUSR | C_IRGRP | C_IROTH);

                final BufferedSource source = Okio.buffer(Okio.source(uri.stream()));
                final long size = uri.size();
                if (size == -1) {
                    final ByteString content = source.readByteString();
                    entry.setSize(content.size());
                    cpio.putArchiveEntry(entry);
                    content.write(cpio);
                } else {
                    entry.setSize(size);
                    cpio.putArchiveEntry(entry);
                    source.readAll(Okio.sink(cpio));
                }

                cpio.closeArchiveEntry();
            }
        }
    }

}