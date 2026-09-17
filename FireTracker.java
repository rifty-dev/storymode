package dev.storymode;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers which fire blocks came from which Enchanted Flint and Steel, so the custom
 * contact debuffs only fire for *our* flames and never for naturally occurring fire.
 *
 * Keyed on block coordinates. Soul fire placed on a block that is not in
 * #minecraft:soul_fire_base_blocks is written without a physics update so it survives;
 * a sweeper task prunes anything that a neighbour update has since destroyed.
 */
public final class FireTracker {

    /** How long a tracked flame lives before the sweeper removes it (ticks). */
    private static final long MAX_AGE_TICKS = 20L * 60L * 5L; // 5 minutes

    public record PlacedFire(UUID placer, FlintVariant variant, long placedTick) {}

    private final Plugin plugin;
    private final Map<Location, PlacedFire> fires = new HashMap<>();
    /** Entities currently burning because of one of our flames -> which variant lit them. */
    private final Map<UUID, FlintVariant> burning = new HashMap<>();

    private BukkitTask sweeper;

    public FireTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        sweeper = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 100L, 100L);
    }

    public void stop() {
        if (sweeper != null) sweeper.cancel();
        fires.clear();
        burning.clear();
    }

    // ------------------------------------------------------------------ fire blocks

    public void track(Block block, UUID placer, FlintVariant variant) {
        fires.put(key(block), new PlacedFire(placer, variant, currentTick()));
    }

    public void untrack(Block block) {
        fires.remove(key(block));
    }

    public PlacedFire at(Block block) {
        return fires.get(key(block));
    }

    /**
     * Finds a tracked flame overlapping the entity's hitbox. FIRE damage means the entity is
     * standing inside a fire block, so this resolves which of our flames is responsible.
     */
    public PlacedFire touching(Entity entity) {
        BoundingBox box = entity.getBoundingBox().expand(0.02D);
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    PlacedFire found = fires.get(new Location(entity.getWorld(), x, y, z));
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ burning entities

    public void markBurning(Entity entity, FlintVariant variant) {
        burning.put(entity.getUniqueId(), variant);
    }

    public FlintVariant burningVariant(Entity entity) {
        return burning.get(entity.getUniqueId());
    }

    public void clearBurning(Entity entity) {
        burning.remove(entity.getUniqueId());
    }

    // ------------------------------------------------------------------ housekeeping

    private void sweep() {
        long now = currentTick();
        Iterator<Map.Entry<Location, PlacedFire>> it = fires.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, PlacedFire> entry = it.next();
            Location loc = entry.getKey();

            // Never force-load chunks just to sweep; unloaded flames are dropped.
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                it.remove();
                continue;
            }
            Block block = loc.getBlock();
            boolean stillFire = block.getType() == FlintVariant.BLUE.fireMaterial()
                    || block.getType() == FlintVariant.GREEN.fireMaterial();
            if (!stillFire || now - entry.getValue().placedTick() > MAX_AGE_TICKS) {
                it.remove();
            }
        }

        burning.entrySet().removeIf(e -> {
            Entity entity = plugin.getServer().getEntity(e.getKey());
            return entity == null || !entity.isValid() || entity.getFireTicks() <= 0;
        });
    }

    private static Location key(Block block) {
        return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private long currentTick() {
        return plugin.getServer().getCurrentTick();
    }
}
