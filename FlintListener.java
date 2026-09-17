package dev.storymode;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Behaviour for both Enchanted Flint and Steel variants.
 *
 * Ignition is deliberately *not* intercepted at PlayerInteractEvent — letting vanilla light the
 * block preserves TNT, campfires, candles and nether portals for free, and adds no delay of its
 * own ("rapid fire"). We then post-process the resulting flame on the next tick: recolour it to
 * soul fire for the blue variant and register it with the FireTracker either way.
 */
public final class FlintListener implements Listener {

    /**
     * The brief asks for a hidden Fire Aspect V *and* for melee hits to apply "ONLY" Weakness.
     * Those two conflict, so the enchantment stays on the item (for tooltip/parity purposes) and
     * the ignition it causes is cancelled here. Set to false to let Fire Aspect actually burn.
     */
    private static final boolean NEUTRALISE_FIRE_ASPECT_ON_HIT = true;

    private static final int BURN_TICKS_ON_FIRE_CONTACT = 200; // 10 seconds

    private final Plugin plugin;
    private final StoryItems items;
    private final FireTracker tracker;

    public FlintListener(Plugin plugin, StoryItems items, FireTracker tracker) {
        this.plugin = plugin;
        this.items = items;
        this.tracker = tracker;
    }

    // ------------------------------------------------------------------ catalyst ability

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        FlintVariant variant = items.flintVariantOf(item);
        if (variant == null) return;

        Player player = event.getPlayer();
        if (player.hasCooldown(item)) return;

        for (PotionEffect effect : variant.catalystEffects()) {
            player.addPotionEffect(effect);
        }

        // Native grey cooldown sweep. The use_cooldown component gives each variant its own
        // cooldown group, so blue and green (and vanilla flint and steel) do not share a timer.
        player.setCooldown(item, StoryItems.CATALYST_COOLDOWN_TICKS);

        player.getWorld().playSound(player.getLocation(), variant.placeSound(),
                SoundCategory.PLAYERS, 0.8F, variant == FlintVariant.BLUE ? 1.6F : 0.9F);

        spawnCatalystRing(player, variant);
    }

    /** Swirling ring of variant-coloured flame particles around the caster. */
    private void spawnCatalystRing(Player player, FlintVariant variant) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick++ > 30 || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double radius = 1.1D;
                double spin = tick * 0.35D;
                double height = 0.15D + (tick % 15) * 0.09D;

                for (int i = 0; i < 8; i++) {
                    double angle = spin + (i * Math.PI / 4.0D);
                    Location point = base.clone().add(
                            Math.cos(angle) * radius,
                            height,
                            Math.sin(angle) * radius);
                    player.getWorld().spawnParticle(variant.particle(), point, 1, 0, 0, 0, 0);
                    if (variant == FlintVariant.GREEN) {
                        player.getWorld().spawnParticle(Particle.SMALL_FLAME, point, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ------------------------------------------------------------------ ignition / fire colour

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) return;
        if (!(event.getIgnitingEntity() instanceof Player player)) return;

        FlintVariant variant = variantInHands(player);
        if (variant == null) return;

        Block block = event.getBlock();
        java.util.UUID placer = player.getUniqueId();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.FIRE) return; // TNT, campfire, portal, etc.

            if (variant.fireMaterial() == Material.SOUL_FIRE) {
                // Soul fire only "survives" above #minecraft:soul_fire_base_blocks, so the write
                // skips the physics update. A neighbour update can still remove it later; the
                // FireTracker sweeper cleans up the bookkeeping when that happens.
                block.setBlockData(Material.SOUL_FIRE.createBlockData(), false);
            }
            tracker.track(block, placer, variant);

            block.getWorld().playSound(block.getLocation(), variant.placeSound(),
                    SoundCategory.BLOCKS, 0.7F, variant == FlintVariant.BLUE ? 1.5F : 1.0F);
        });
    }

    // ------------------------------------------------------------------ melee hits

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        FlintVariant variant = items.flintVariantOf(player.getInventory().getItemInMainHand());
        if (variant == null) return;

        victim.addPotionEffect(new PotionEffect(
                org.bukkit.potion.PotionEffectType.WEAKNESS,
                200, variant.weaknessOnHitAmplifier(), true, false, true));

        if (NEUTRALISE_FIRE_ASPECT_ON_HIT) {
            int fireBefore = victim.getFireTicks();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (victim.isValid() && victim.getFireTicks() > fireBefore) {
                    victim.setFireTicks(Math.max(0, fireBefore));
                }
            });
        }
    }

    // ------------------------------------------------------------------ custom fire debuff

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.FIRE) {
            FireTracker.PlacedFire source = tracker.touching(victim);
            if (source == null) return;

            applyFireDebuff(victim, source.variant());
            tracker.markBurning(victim, source.variant());
            victim.setFireTicks(BURN_TICKS_ON_FIRE_CONTACT);
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            // Keeps the debuff topped up while the entity burns from one of our flames.
            FlintVariant variant = tracker.burningVariant(victim);
            if (variant != null) {
                applyFireDebuff(victim, variant);
            }
        }
    }

    private void applyFireDebuff(LivingEntity victim, FlintVariant variant) {
        for (PotionEffect effect : variant.fireContactEffects()) {
            victim.addPotionEffect(effect);
        }
    }

    // ------------------------------------------------------------------ indestructibility

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (items.flintVariantOf(item) == null) return;

        event.setCancelled(true);
        if (item.getItemMeta() instanceof Damageable damageable && damageable.getDamage() != 0) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
        }
    }

    // ------------------------------------------------------------------ helpers

    private FlintVariant variantInHands(Player player) {
        FlintVariant main = items.flintVariantOf(player.getInventory().getItemInMainHand());
        if (main != null) return main;
        return items.flintVariantOf(player.getInventory().getItemInOffHand());
    }
}
