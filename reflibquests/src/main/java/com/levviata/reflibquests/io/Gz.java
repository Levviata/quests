/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.Snbt;

/** Compressed text form of a data tree, for the network. Decompression is size-limited. */
public final class Gz {

    /** Upper bound for decompressed data, protects against decompression bombs. */
    public static final int MAX_UNPACKED_BYTES = 64 * 1024 * 1024;

    private Gz() {}

    public static byte[] pack(DataNode.Compound data) {
        byte[] text = Snbt.printCompact(data).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, text.length / 6));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(text);
        } catch (IOException ex) {
            throw new IllegalStateException(ex); // cannot happen for a memory stream
        }
        return out.toByteArray();
    }

    /**
     * @throws IllegalArgumentException if the data is not valid gzip text data or is too large
     */
    public static DataNode.Compound unpack(byte[] packed) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_UNPACKED_BYTES) {
                    throw new IllegalArgumentException("unpacked data exceeds " + MAX_UNPACKED_BYTES + " bytes");
                }
                out.write(buf, 0, n);
            }
            return Snbt.parse(out.toString(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("bad packed data: " + ex.getMessage(), ex);
        }
    }
}
