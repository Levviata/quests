/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Reassembler<K> {

    public static final int MAX_PENDING_PER_SENDER = 4;
    public static final long TIMEOUT_MILLIS = 30_000;

    private static final class Pending {
        final int count;
        final byte[][] parts;
        int received;
        int bytes;
        final long startedAt;

        Pending(int count, long startedAt) {
            this.count = count;
            this.parts = new byte[count][];
            this.startedAt = startedAt;
        }
    }

    private final int maxFragments;
    private final int maxTotalBytes;
    private final Map<K, Map<Integer, Pending>> bySender = new HashMap<>();

    public Reassembler(int maxFragments, int maxTotalBytes) {
        this.maxFragments = maxFragments;
        this.maxTotalBytes = maxTotalBytes;
    }

    public synchronized byte[] accept(K sender, int transfer, int index, int count, byte[] fragment, long nowMillis) {
        if (count < 1 || count > maxFragments) {
            throw new IllegalArgumentException("Fragment count " + count + " outside 1.." + maxFragments);
        }
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("Fragment index " + index + " outside 0.." + (count - 1));
        }
        if (count == 1) {
            if (fragment.length > maxTotalBytes) {
                throw new IllegalArgumentException("Message of " + fragment.length + " bytes exceeds " + maxTotalBytes);
            }
            return fragment;
        }
        Map<Integer, Pending> transfers = bySender.computeIfAbsent(sender, k -> new HashMap<>());
        expire(transfers, nowMillis);
        Pending p = transfers.get(transfer);
        if (p == null) {
            if (transfers.size() >= MAX_PENDING_PER_SENDER) {
                throw new IllegalArgumentException("Too many unfinished transfers from one sender");
            }
            p = new Pending(count, nowMillis);
            transfers.put(transfer, p);
        } else if (p.count != count) {
            transfers.remove(transfer);
            throw new IllegalArgumentException("Fragment count changed within transfer " + transfer);
        }
        if (p.parts[index] != null) {
            transfers.remove(transfer);
            throw new IllegalArgumentException("Duplicate fragment " + index + " in transfer " + transfer);
        }
        if ((long) p.bytes + fragment.length > maxTotalBytes) {
            transfers.remove(transfer);
            throw new IllegalArgumentException("Transfer " + transfer + " exceeds " + maxTotalBytes + " bytes");
        }
        p.parts[index] = fragment;
        p.bytes += fragment.length;
        p.received++;
        if (p.received < p.count) {
            return null;
        }
        transfers.remove(transfer);
        if (transfers.isEmpty()) {
            bySender.remove(sender);
        }
        byte[] whole = new byte[p.bytes];
        int offset = 0;
        for (byte[] part : p.parts) {
            System.arraycopy(part, 0, whole, offset, part.length);
            offset += part.length;
        }
        return whole;
    }

    public synchronized void clear(K sender) {
        bySender.remove(sender);
    }

    private static void expire(Map<Integer, Pending> transfers, long now) {
        for (Iterator<Pending> it = transfers.values().iterator(); it.hasNext(); ) {
            if (now - it.next().startedAt > TIMEOUT_MILLIS) {
                it.remove();
            }
        }
    }
}
