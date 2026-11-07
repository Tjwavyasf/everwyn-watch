package dev.mrwavy.everwynwatch;

import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places .nbt structure files (the format of structure blocks, Create schematics and many build sites)
 * from the app, and can undo the last few placements. The area has to be loaded (a player nearby), so it
 * never loads or generates chunks on its own.
 */
final class SchematicPlacer {
    private static final int MAX_FILE = 8 * 1024 * 1024;
    private static final int MAX_VOLUME = 200_000;

    private record Undo(ServerWorld world, List<BlockPos> pos, List<BlockState> states, Map<BlockPos, NbtCompound> blockEntities, String name) {}

    private NbtCompound pending;
    private String pendingName = "";
    private final Deque<Undo> undo = new ArrayDeque<>();

    /** Web thread: reads an uploaded .nbt file and keeps it ready to place. */
    synchronized JsonObject upload(InputStream in, String name) throws Exception {
        byte[] data = in.readNBytes(MAX_FILE + 1);
        if (data.length > MAX_FILE) throw new IllegalArgumentException("That file is over 8 MB.");
        NbtCompound nbt;
        try {
            nbt = NbtIo.readCompressed(new ByteArrayInputStream(data));
        } catch (Exception gzipFailed) {
            try {
                nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(data)));
            } catch (Exception e) {
                throw new IllegalArgumentException("That isn't an .nbt structure file.");
            }
        }
        if (nbt == null || !nbt.contains("size", NbtElement.LIST_TYPE) || !nbt.contains("blocks", NbtElement.LIST_TYPE)) {
            throw new IllegalArgumentException("That file has no structure in it. Use a vanilla .nbt structure file (from a structure block or Create); .schem and .litematic aren't supported.");
        }
        NbtList size = nbt.getList("size", NbtElement.INT_TYPE);
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        if ((long) sx * sy * sz > MAX_VOLUME) throw new IllegalArgumentException("That build is too big (" + sx + "×" + sy + "×" + sz + "). The limit is " + MAX_VOLUME + " blocks of space.");
        pending = nbt;
        pendingName = name == null ? "schematic" : name.replaceAll("[^A-Za-z0-9 _.\\-]", "");
        JsonObject o = new JsonObject();
        o.addProperty("name", pendingName);
        o.addProperty("x", sx);
        o.addProperty("y", sy);
        o.addProperty("z", sz);
        o.addProperty("blocks", nbt.getList("blocks", NbtElement.COMPOUND_TYPE).size());
        return o;
    }

    /** Server thread. rot is 0, 90, 180 or 270 degrees clockwise; the build's corner goes at x,y,z. */
    synchronized String place(ServerWorld world, int x, int y, int z, int rot) {
        if (pending == null) throw new IllegalArgumentException("Upload a schematic first.");
        StructureTemplate template = world.getServer().getStructureTemplateManager().createTemplate(pending);
        BlockRotation rotation = switch (((rot % 360) + 360) % 360) {
            case 90 -> BlockRotation.CLOCKWISE_90;
            case 180 -> BlockRotation.CLOCKWISE_180;
            case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
        StructurePlacementData data = new StructurePlacementData().setRotation(rotation).setIgnoreEntities(true);
        BlockPos origin = new BlockPos(x, y, z);
        BlockBox box = template.calculateBoundingBox(data, origin);
        if (box.getMinY() < world.getBottomY() || box.getMaxY() >= world.getTopY()) throw new IllegalArgumentException("It doesn't fit between the bottom and top of the world here.");
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) throw new IllegalArgumentException("Part of that area isn't loaded. Have a player stand nearby, then try again.");
            }
        }
        // remember what was there, to undo
        List<BlockPos> pos = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        Map<BlockPos, NbtCompound> bes = new HashMap<>();
        for (BlockPos p : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            BlockPos im = p.toImmutable();
            pos.add(im);
            states.add(world.getBlockState(im));
            BlockEntity be = world.getBlockEntity(im);
            if (be != null) bes.put(im, be.createNbtWithIdentifyingData());
        }
        boolean ok = template.place(world, origin, origin, data, Random.create(), Block.NOTIFY_LISTENERS);
        if (!ok) throw new IllegalArgumentException("Minecraft couldn't place that structure.");
        undo.push(new Undo(world, pos, states, bes, pendingName));
        while (undo.size() > 3) undo.removeLast();
        Vec3i size = template.getRotatedSize(rotation);
        return "Placed " + pendingName + " (" + size.getX() + "×" + size.getY() + "×" + size.getZ() + ") at " + x + ", " + y + ", " + z;
    }

    /** Server thread: puts back what was there before the last placement. */
    synchronized String undoLast() {
        Undo u = undo.poll();
        if (u == null) throw new IllegalArgumentException("Nothing to undo.");
        for (int i = 0; i < u.pos.size(); i++) u.world.setBlockState(u.pos.get(i), u.states.get(i), Block.NOTIFY_LISTENERS);
        for (Map.Entry<BlockPos, NbtCompound> e : u.blockEntities.entrySet()) {
            BlockEntity be = u.world.getBlockEntity(e.getKey());
            if (be != null) be.readNbt(e.getValue());
        }
        return "Removed " + u.name + " and put back what was there";
    }

    synchronized JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("ready", pending != null);
        o.addProperty("name", pendingName);
        o.addProperty("undo", undo.size());
        return o;
    }
}
