/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.assets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Internal: a case-insensitive index of the {@code assets/<domain>/...} entries of a zip or jar. Case-insensitive
 * because assets written for older or newer game versions do not always use the lower-case names 1.12.2 asks for.
 * Only the listed domains are exposed; the {@code minecraft} domain is never exposed. Nothing is extracted or
 * copied to disk, and nothing in the archive is ever loaded as code.
 */
public final class ZipAssetIndex implements AutoCloseable {

    private final File file;
    private final ZipFile zip;
    private final Set<String> domains = new HashSet<>();
    /** lower-case "assets/domain/path" to the real entry name */
    private final Map<String, String> index = new HashMap<>();

    /**
     * @param wanted domains to expose, or {@code null} for every domain except {@code minecraft}
     */
    public ZipAssetIndex(File file, Collection<String> wanted) throws IOException {
        this.file = file;
        this.zip = new ZipFile(file);
        Set<String> want = null;
        if (wanted != null) {
            want = new HashSet<>();
            for (String d : wanted) {
                want.add(d.toLowerCase(Locale.ROOT));
            }
        }
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            String lower = e.getName().toLowerCase(Locale.ROOT);
            if (!lower.startsWith("assets/")) {
                continue;
            }
            int slash = lower.indexOf('/', 7);
            if (slash < 0) {
                continue;
            }
            String domain = lower.substring(7, slash);
            if (domain.equals("minecraft") || want != null && !want.contains(domain)) {
                continue;
            }
            domains.add(domain);
            index.putIfAbsent(lower, e.getName());
        }
    }

    public File file() {
        return file;
    }

    public Set<String> domains() {
        return Collections.unmodifiableSet(domains);
    }

    public boolean has(String domain, String path) {
        return index.containsKey(key(domain, path));
    }

    /** @return the bytes of the entry, or null if it does not exist */
    public byte[] read(String domain, String path) throws IOException {
        String real = index.get(key(domain, path));
        if (real == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(zip.getEntry(real))) {
            return in.readAllBytes();
        }
    }

    /** @return every exposed entry as a lower-case {@code assets/domain/path} name */
    public java.util.List<String> keys() {
        return new java.util.ArrayList<>(index.keySet());
    }

    /** @return the number of exposed files */
    public int size() {
        return index.size();
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    static String key(String domain, String path) {
        return ("assets/" + domain + "/" + path).toLowerCase(Locale.ROOT);
    }
}
