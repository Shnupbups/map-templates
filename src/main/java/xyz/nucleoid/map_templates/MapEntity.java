package xyz.nucleoid.map_templates;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class MapEntity {
    private final Vec3d position;
    final NbtCompound tag;

    public MapEntity(Vec3d position, NbtCompound tag) {
        this.position = position;
        this.tag = tag;
    }

    public Vec3d getPosition() {
        return this.position;
    }

    public NbtCompound createEntityTag(BlockPos origin) {
        var tag = this.tag.copy();

        var chunkLocalPos = listToPos(this.tag.getList("Pos", NbtElement.DOUBLE_TYPE));

        var worldPosition = this.position.add(origin.getX(), origin.getY(), origin.getZ());
        tag.put("Pos", posToList(worldPosition));

        if (tag.contains("TileX", NbtElement.INT_TYPE)) {
            tag.putInt("TileX", MathHelper.floor(tag.getInt("TileX") + worldPosition.x - chunkLocalPos.x));
            tag.putInt("TileY", MathHelper.floor(tag.getInt("TileY") + worldPosition.y - chunkLocalPos.y));
            tag.putInt("TileZ", MathHelper.floor(tag.getInt("TileZ") + worldPosition.z - chunkLocalPos.z));
        }

        return tag;
    }

    public void createEntities(World world, BlockPos origin, Consumer<Entity> consumer) {
        var tag = this.createEntityTag(origin);
        EntityType.loadEntityWithPassengers(tag, world, entity -> {
            consumer.accept(entity);
            return entity;
        });
    }

    @Nullable
    public static MapEntity fromEntity(Entity entity, Vec3d position) {
        var tag = new NbtCompound();
        if (!entity.saveNbt(tag)) {
            return null;
        }

        // Avoid conflicts.
        tag.remove("UUID");

        BlockPos minChunkPos = getMinChunkPosFor(position);
        tag.put("Pos", posToList(position.subtract(minChunkPos.getX(), minChunkPos.getY(), minChunkPos.getZ())));

        // AbstractDecorationEntity has special position handling with an attachment position.
        if (tag.contains("TileX", NbtElement.INT_TYPE)) {
            BlockPos localPos = new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ"))
                    .subtract(entity.getBlockPos())
                    .add(position.getX(), position.getY(), position.getZ())
                    .subtract(minChunkPos);
            tag.putInt("TileX", localPos.getX());
            tag.putInt("TileY", localPos.getY());
            tag.putInt("TileZ", localPos.getZ());
        }

        return new MapEntity(position, tag);
    }

    public static MapEntity fromTag(ChunkSectionPos sectionPos, NbtCompound tag) {
        Vec3d localPos = listToPos(tag.getList("Pos", NbtElement.DOUBLE_TYPE));
        Vec3d globalPos = localPos.add(sectionPos.getMinX(), sectionPos.getMinY(), sectionPos.getMinZ());

        return new MapEntity(globalPos, tag);
    }

    MapEntity transformed(MapTransform transform) {
        var resultPosition = transform.transformedPoint(this.position);
        var resultTag = this.tag.copy();

        var minChunkPos = getMinChunkPosFor(this.position);
        var minResultChunkPos = getMinChunkPosFor(resultPosition);

        resultTag.put("Pos", posToList(resultPosition.subtract(minResultChunkPos.getX(), minResultChunkPos.getY(), minResultChunkPos.getZ())));

        // AbstractDecorationEntity has special position handling with an attachment position.
        if (resultTag.contains("TileX", NbtElement.INT_TYPE)) {
            var attachedPos = new BlockPos(
                    resultTag.getInt("TileX") + minChunkPos.getX(),
                    resultTag.getInt("TileY") + minChunkPos.getY(),
                    resultTag.getInt("TileZ") + minChunkPos.getZ()
            );

            var localAttachedPos = transform.transformedPoint(attachedPos)
                    .subtract(minResultChunkPos);
            resultTag.putInt("TileX", localAttachedPos.getX());
            resultTag.putInt("TileY", localAttachedPos.getY());
            resultTag.putInt("TileZ", localAttachedPos.getZ());
        }

        return new MapEntity(resultPosition, resultTag);
    }

    private static BlockPos getMinChunkPosFor(Vec3d position) {
        return new BlockPos(
                MathHelper.floor(position.getX()) & ~15,
                MathHelper.floor(position.getY()) & ~15,
                MathHelper.floor(position.getZ()) & ~15
        );
    }

    private static NbtList posToList(Vec3d pos) {
        var list = new NbtList();
        list.add(NbtDouble.of(pos.x));
        list.add(NbtDouble.of(pos.y));
        list.add(NbtDouble.of(pos.z));
        return list;
    }

    private static Vec3d listToPos(NbtList list) {
        return new Vec3d(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }
}
