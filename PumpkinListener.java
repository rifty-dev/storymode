package dev.storymode;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * White Pumpkin behaviour.
 *
 * Carved pumpkins are not armour, so none of this is free: the armour values come from custom
 * AttributeModifiers on the item, and the durability that drives the cracked texture is applied
 * here by mimicking vanilla armour wear (including the hidden Unbreaking III roll).
 */
public final class PumpkinListener implements Listener {

    /** Team used purely to switch the wearer's nametag off. */
    private static final String STALKER_TEAM = "mcsm_faceless";

    /** Damage causes that bypass armour in vanilla, so they should not wear the helmet either. */
    private static final Set<EntityDamageEvent.DamageCause> ARMOUR_BYPASSING = EnumSet.of(
            EntityDamageEvent.DamageCause.STARVATION,
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.WITHER,
            EntityDamageEvent.DamageCause.POISON,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.FLY_INTO_WALL,
            EntityDamageEvent.DamageCause.KILL,
            EntityDamageEvent.DamageCause.CUSTOM
    );

    private final Plugin plugin;
    private final Keys keys;
    private final StoryItems items;
    private final Random random = new Random();

    private Team stalkerTeam;

    public PumpkinListener(Plugin plugin, Keys keys, StoryItems items) {
        this.plugin = plugin;
        this.keys = keys;
        this.items = items;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        stalkerTeam = board.getTeam(STALKER_TEAM);
        if (stalkerTeam == null) {
            stalkerTeam = board.registerNewTeam(STALKER_TEAM);
        }
        stalkerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        // One sync loop covers nametag membership, the axe attack-speed modifier and the
        // cracked/pristine model, so nothing can drift out of step with the worn item.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::syncAll, 20L, 5L);
    }

    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            setStalker(player, false);
            clearAxeModifier(player);
        }
    }

    // ------------------------------------------------------------------ per-tick sync

    private void syncAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean wearing = isWearing(player);

            setStalker(player, wearing);
            syncAxePrecision(player, wearing);

            if (wearing) {
                ItemStack helmet = player.getInventory().getHelmet();
                if (syncModel(helmet)) {
                    player.getInventory().setHelmet(helmet);
                }
                // Hostile mobs already locked on lose interest while the mask is worn.
                clearNearbyAggro(player);
            }
        }
    }

    private boolean isWearing(Player player) {
        return items.isWhitePumpkin(player.getInventory().getHelmet());
    }

    // ------------------------------------------------------------------ passive 1: faceless stalker

    private void setStalker(Player player, boolean active) {
        if (stalkerTeam == null) return;
        boolean member = stalkerTeam.hasEntry(player.getName());
        if (active && !member) {
            stalkerTeam.addEntry(player.getName());
        } else if (!active && member) {
            stalkerTeam.removeEntry(player.getName());
        }
    }

    private void clearNearbyAggro(Player player) {
        for (Mob mob : player.getWorld().getNearbyEntitiesByType(Mob.class, player.getLocation(), 24.0D)) {
            if (mob instanceof Monster && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Monster)) return;
        if (event.getReason() == EntityTargetEvent.TargetReason.CUSTOM) return;
        if (!isWearing(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        setStalker(event.getPlayer(), false);
        clearAxeModifier(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setStalker(event.getPlayer(), isWearing(event.getPlayer()));
    }

    // ------------------------------------------------------------------ passive 2: axe precision

    private void syncAxePrecision(Player player, boolean wearing) {
        AttributeInstance instance = player.getAttribute(Attribute.ATTACK_SPEED);
        if (instance == null) return;

        clearAxeModifier(player);

        if (!wearing) return;
        if (!isAxe(player.getInventory().getItemInMainHand())) return;

        // getValue() already includes the held axe's own attack-speed modifier, so the delta
        // needed is simply the gap up to a sword. Works for every axe tier automatically.
        double delta = StoryItems.SWORD_ATTACK_SPEED - instance.getValue();
        if (delta <= 0.0001D) return;

        instance.addTransientModifier(new AttributeModifier(
                keys.axePrecision, delta,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void clearAxeModifier(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.ATTACK_SPEED);
        if (instance == null) return;
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (keys.axePrecision.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }

    private static boolean isAxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
    }

    // ------------------------------------------------------------------ wear + dynamic cracking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWearerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFinalDamage() <= 0.0D) return;
        if (ARMOUR_BYPASSING.contains(event.getCause())) return;

        ItemStack helmet = player.getInventory().getHelmet();
        if (!items.isWhitePumpkin(helmet)) return;
        if (!(helmet.getItemMeta() instanceof Damageable damageable)) return;

        // Vanilla armour Unbreaking: chance to avoid wear = 0.6 + 0.4 / (level + 1).
        int unbreaking = helmet.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
        if (unbreaking > 0 && random.nextDouble() < 0.6D + 0.4D / (unbreaking + 1)) return;

        int wear = (int) Math.max(1.0D, event.getDamage() / 4.0D);
        int newDamage = damageable.getDamage() + wear;

        if (newDamage >= StoryItems.PUMPKIN_MAX_DAMAGE) {
            player.getInventory().setHelmet(null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
            return;
        }

        damageable.setDamage(newDamage);
        helmet.setItemMeta(damageable);
        syncModel(helmet);
        player.getInventory().setHelmet(helmet);
    }

    /**
     * Points the item at the pristine (1001) or cracked (1002) model based on remaining
     * durability. Reversible: repairing back above the threshold restores the pristine look.
     *
     * @return true if the model actually changed
     */
    public boolean syncModel(ItemStack item) {
        if (!items.isWhitePumpkin(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;

        int remaining = StoryItems.PUMPKIN_MAX_DAMAGE - damageable.getDamage();
        int wanted = remaining >= StoryItems.CRACK_THRESHOLD
                ? StoryItems.PUMPKIN_MODEL_PRISTINE
                : StoryItems.PUMPKIN_MODEL_CRACKED;

        Integer current = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        if (current != null && current == wanted) return false;

        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) wanted));
        meta.setCustomModelDataComponent(component);
        meta.setCustomModelData(wanted);

        item.setItemMeta(meta);
        return true;
    }

    /** Carved pumpkins are not a vanilla armour material, so nothing here relies on Material checks. */
    static boolean isCarvedPumpkin(ItemStack item) {
        return item != null && item.getType() == Material.CARVED_PUMPKIN;
    }
}
