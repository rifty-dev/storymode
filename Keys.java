package dev.storymode;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central registry of every NamespacedKey the plugin uses.
 *
 * Attribute modifiers are keyed (not UUID'd) in 1.21+, so these keys double as the
 * identity of each modifier — reusing the same key overwrites rather than stacks.
 */
public final class Keys {

    /** PersistentDataContainer key holding the item id ("white_pumpkin", "flint_blue", ...). */
    public final NamespacedKey itemId;

    /** Attribute modifier keys for the White Pumpkin. */
    public final NamespacedKey pumpkinArmor;
    public final NamespacedKey pumpkinToughness;
    public final NamespacedKey pumpkinKnockback;

    /** Attribute modifier key applied to the *player* for the Axe Precision passive. */
    public final NamespacedKey axePrecision;

    /** use_cooldown component groups — one per variant, so the two flints cool down independently. */
    public final NamespacedKey cooldownBlue;
    public final NamespacedKey cooldownGreen;

    public Keys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.pumpkinArmor = new NamespacedKey(plugin, "white_pumpkin_armor");
        this.pumpkinToughness = new NamespacedKey(plugin, "white_pumpkin_toughness");
        this.pumpkinKnockback = new NamespacedKey(plugin, "white_pumpkin_knockback");
        this.axePrecision = new NamespacedKey(plugin, "axe_precision");
        this.cooldownBlue = new NamespacedKey(plugin, "flint_blue");
        this.cooldownGreen = new NamespacedKey(plugin, "flint_green");
    }
}
