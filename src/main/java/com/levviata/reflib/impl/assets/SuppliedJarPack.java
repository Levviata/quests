/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.assets;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.assets.SuppliedAssets;

/**
 * Internal: serves the exposed domains of one player-supplied jar as a resource pack. A request for
 * {@code lang/xx_yy.lang} that the jar only has as {@code lang/xx_yy.json} is answered with a converted copy.
 */
@SideOnly(Side.CLIENT)
public final class SuppliedJarPack implements IResourcePack {

    private final ZipAssetIndex index;

    public SuppliedJarPack(ZipAssetIndex index) {
        this.index = index;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String domain = location.getNamespace();
        String path = SuppliedAssets.resolveAlias(domain, location.getPath());
        byte[] data = index.read(domain, path);
        if (data == null && path.toLowerCase().endsWith(".lang")) {
            byte[] json = index.read(domain, path.substring(0, path.length() - 5) + ".json");
            if (json != null) {
                data = LangJson.toLang(json);
            }
        }
        if (data == null) {
            throw new FileNotFoundException(location.toString());
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        String domain = location.getNamespace();
        if (!index.domains().contains(domain)) {
            return false;
        }
        String path = SuppliedAssets.resolveAlias(domain, location.getPath());
        if (index.has(domain, path)) {
            return true;
        }
        return path.toLowerCase().endsWith(".lang")
                && index.has(domain, path.substring(0, path.length() - 5) + ".json");
    }

    @Override
    public Set<String> getResourceDomains() {
        return index.domains();
    }

    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        throw new FileNotFoundException("pack.png");
    }

    @Override
    public String getPackName() {
        return "ReflibSupplied:" + index.file().getName();
    }
}
