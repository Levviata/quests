/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.assets.SuppliedAssets;
import com.levviata.reflib.impl.assets.ZipAssetIndex;

@SideOnly(Side.CLIENT)
final class ImageCatalog {

    record Entry(String id, String domain, ResourceLocation location, EntityPainting.EnumArt art) {
        String modName() {
            if (domain.equals("minecraft")) {
                return "Minecraft";
            }
            ModContainer mod = Loader.instance().getIndexedModList().get(domain);
            if (mod != null) {
                return mod.getName();
            }
            return domain.isEmpty() ? domain : Character.toUpperCase(domain.charAt(0)) + domain.substring(1);
        }
    }

    private static List<Entry> all;
    private static final java.util.Map<String, int[]> SIZES = new java.util.HashMap<>();
    private static final ResourceLocation PAINTING_SHEET = new ResourceLocation("textures/painting/paintings_kristoffer_zetterstrand.png");

    private ImageCatalog() {}

    static synchronized List<Entry> all() {
        if (all == null) {
            all = scan();
        }
        return all;
    }

    static synchronized void reset() {
        all = null;
    }

    private static List<Entry> scan() {
        Set<File> sources = new HashSet<>();
        for (ModContainer mod : Loader.instance().getModList()) {
            addSource(sources, mod.getSource());
        }
        try {
            java.security.CodeSource cs = Minecraft.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                addSource(sources, new File(cs.getLocation().toURI()));
            }
        } catch (java.net.URISyntaxException | RuntimeException ignored) {

        }
        ResourcePackRepository repo = Minecraft.getMinecraft().getResourcePackRepository();
        for (ResourcePackRepository.Entry e : repo.getRepositoryEntries()) {
            IResourcePack pack = e.getResourcePack();
            if (pack instanceof AbstractResourcePack abstractPack) {
                addSource(sources, packFile(abstractPack));
            }
        }
        Set<String> ids = new HashSet<>();
        List<Entry> out = new ArrayList<>();
        for (File source : sources) {
            try {
                if (source.isDirectory()) {
                    scanFolder(source.toPath(), ids, out);
                } else if (source.isFile()) {
                    scanZip(source, ids, out);
                }
            } catch (IOException | RuntimeException ex) {

            }
        }
        for (ZipAssetIndex idx : SuppliedAssets.sources()) {
            for (String key : idx.keys()) {
                add("assets/", key, key, ids, out);
            }
        }
        out.sort(Comparator.comparing((Entry e) -> !e.domain().equals("minecraft")).thenComparing(Entry::domain).thenComparing(Entry::id));
        List<Entry> result = new ArrayList<>();

        for (EntityPainting.EnumArt art : EntityPainting.EnumArt.values()) {
            String name = art.title.toLowerCase(Locale.ROOT);
            result.add(new Entry("minecraft:" + name, "minecraft", null, art));
        }
        result.addAll(out);
        return result;
    }

    private static File packFile(AbstractResourcePack pack) {
        for (Class<?> c = pack.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType() == File.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        return (File) f.get(pack);
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static void addSource(Set<File> sources, File f) {
        if (f != null && f.exists()) {
            sources.add(f.getAbsoluteFile());
        }
    }

    private static void scanZip(File file, Set<String> ids, List<Entry> out) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) {
                    add("assets/", e.getName(), e.getName(), ids, out);
                }
            }
        }
    }

    private static void scanFolder(Path root, Set<String> ids, List<Entry> out) throws IOException {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(assets)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = "assets/" + assets.relativize(p).toString().replace('\\', '/');
                add("assets/", rel, rel, ids, out);
            });
        }
    }

    private static void add(String prefix, String name, String original, Set<String> ids, List<Entry> out) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(prefix) || !lower.endsWith(".png")) {
            return;
        }
        int slash = lower.indexOf('/', prefix.length());
        if (slash < 0) {
            return;
        }
        String domain = lower.substring(prefix.length(), slash);
        String path = lower.substring(slash + 1);
        if (!path.startsWith("textures/") || domain.isEmpty()) {
            return;
        }
        String id = domain + ":" + path;
        if (ids.add(id)) {
            try {
                out.add(new Entry(id, domain, new ResourceLocation(domain, path), null));
            } catch (RuntimeException ex) {
                ids.remove(id);
            }
        }
    }

    static EntityPainting.EnumArt paintingOf(String id) {
        int colon = id.indexOf(':');
        String name = colon < 0 ? id : id.substring(colon + 1);
        if (name.indexOf('/') >= 0 || name.indexOf('.') >= 0) {
            return null;
        }
        for (EntityPainting.EnumArt art : EntityPainting.EnumArt.values()) {
            if (art.title.equalsIgnoreCase(name.replace("_", ""))) {
                return art;
            }
        }
        return null;
    }

    static void draw(Minecraft mc, String id, double x, double y, double size) {
        EntityPainting.EnumArt art = paintingOf(id);
        ResourceLocation tex;
        double w;
        double h;
        double u0 = 0;
        double u1 = 1;
        double v0 = 0;
        double v1 = 1;
        if (art != null) {
            tex = PAINTING_SHEET;
            w = art.sizeX;
            h = art.sizeY;
            u0 = art.offsetX / 256.0;
            v0 = art.offsetY / 256.0;
            u1 = (art.offsetX + art.sizeX) / 256.0;
            v1 = (art.offsetY + art.sizeY) / 256.0;
        } else {
            try {
                tex = new ResourceLocation(id);
            } catch (RuntimeException ex) {
                return;
            }
            final ResourceLocation bound = tex;
            int[] dim = SIZES.computeIfAbsent(id, k -> {
                mc.getTextureManager().bindTexture(bound);
                return new int[] {Math.max(1, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)),
                    Math.max(1, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT))};
            });
            w = dim[0];
            h = dim[1];
            if (h >= 2 * w && h % w == 0) {
                v1 = w / h;
                h = w;
            }
        }
        double scale = size / Math.max(w, h);
        double dw = w * scale;
        double dh = h * scale;
        double dx = x + (size - dw) / 2;
        double dy = y + (size - dh) / 2;
        mc.getTextureManager().bindTexture(tex);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bb.pos(dx, dy + dh, 0).tex(u0, v1).endVertex();
        bb.pos(dx + dw, dy + dh, 0).tex(u1, v1).endVertex();
        bb.pos(dx + dw, dy, 0).tex(u1, v0).endVertex();
        bb.pos(dx, dy, 0).tex(u0, v0).endVertex();
        tess.draw();
    }
}
