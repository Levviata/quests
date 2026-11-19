/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.net;

import net.minecraft.network.PacketBuffer;

/**
 * A message sent through a {@link ReflibChannel}. Implementations need a public no-argument constructor
 * (used when decoding) and must write and read exactly the same fields in the same order.
 */
public interface ReflibMessage {

    void write(PacketBuffer buf);

    void read(PacketBuffer buf);
}
