/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.net;

import net.minecraft.network.PacketBuffer;

public interface ReflibMessage {

    void write(PacketBuffer buf);

    void read(PacketBuffer buf);
}
