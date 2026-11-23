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

public final class ZipAssetIndex implements AutoCloseable {

    private final File file;
    private final ZipFile zip;
    private final Set<String> domains = new HashSet<>();

    private final Map<String, String> index = new HashMap<>();

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

    public byte[] read(String domain, String path) throws IOException {
        String real = index.get(key(domain, path));
        if (real == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(zip.getEntry(real))) {
            return in.readAllBytes();
        }
    }

    public java.util.List<String> keys() {
        return new java.util.ArrayList<>(index.keySet());
    }

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
