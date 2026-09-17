package dev.storymode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds and identifies the custom items.
 *
 * Everything cosmetic obeys the brief: no lore, no italics, legacy colour codes only,
 * glint override off, and every vanilla tooltip section that would add text is hidden.
 */
public final class StoryItems {

    public static final String WHITE_PUMPKIN_ID = "white_pumpkin";
    public static final String WHITE_PUMPKIN_NAME = "§4White Pumpkin";

    public static final int PUMPKIN_MODEL_PRISTINE = 1001;
    public static final int PUMPKIN_MODEL_CRACKED = 1002;

    /** Netherite helmet durability. */
    public static final int PUMPKIN_MAX_DAMAGE = 407;
    /** Remaining-durability threshold at which the texture cracks. */
    public static final int CRACK_THRESHOLD = 100;

    public static final double PUMPKIN_ARMOR = 3.0D;
    public static final double PUMPKIN_TOUGHNESS = 3.0D;
    /**
     * Requested as "+1". Knockback resistance is a 0..1 scale where 1.0 is *total* knockback
     * immunity — a real netherite helmet grants 0.10. Change to 0.10D for vanilla parity.
     */
    public static final double PUMPKIN_KNOCKBACK_RESISTANCE = 1.0D;

    /** Target attack speed for Axe Precision — diamond/netherite sword. */
    public static final double SWORD_ATTACK_SPEED = 1.6D;

    public static final int CATALYST_COOLDOWN_TICKS = 1200;

    private final Keys keys;

    public StoryItems(Keys keys) {
        this.keys = keys;
    }

    // ------------------------------------------------------------------ builders

    public ItemStack whitePumpkin() {
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = item.getItemMeta();

        name(meta, WHITE_PUMPKIN_NAME);
        meta.lore(null);
        modelData(meta, PUMPKIN_MODEL_PRISTINE);
        meta.setEnchantmentGlintOverride(false);

        // max_damage requires max_stack_size = 1, otherwise the client rejects the component.
        meta.setMaxStackSize(1);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(PUMPKIN_MAX_DAMAGE);
            damageable.setDamage(0);
        }

        // Hidden Unbreaking III — reduces how often PumpkinListener applies wear.
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);

        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                keys.pumpkinArmor, PUMPKIN_ARMOR,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                keys.pumpkinToughness, PUMPKIN_TOUGHNESS,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                keys.pumpkinKnockback, PUMPKIN_KNOCKBACK_RESISTANCE,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));

        hideEverything(meta);
        tag(meta, WHITE_PUMPKIN_ID);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack flint(FlintVariant variant) {
        ItemStack item = new ItemStack(Material.FLINT_AND_STEEL);
        ItemMeta meta = item.getItemMeta();

        name(meta, variant.displayName());
        meta.lore(null);
        modelData(meta, variant.modelData());
        meta.setEnchantmentGlintOverride(false);

        // Indestructible. Unbreakable already stops all wear; PumpkinListener's sibling
        // handler also cancels PlayerItemDamageEvent and zeroes damage as a belt-and-braces reset.
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 5, true);

        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(CATALYST_COOLDOWN_TICKS / 20.0F);
        cooldown.setCooldownGroup(cooldownGroup(variant));
        meta.setUseCooldown(cooldown);

        hideEverything(meta);
        tag(meta, variant.id());

        item.setItemMeta(meta);
        return item;
    }

    public NamespacedKey cooldownGroup(FlintVariant variant) {
        return variant == FlintVariant.BLUE ? keys.cooldownBlue : keys.cooldownGreen;
    }

    // ------------------------------------------------------------------ identity

    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(keys.itemId, PersistentDataType.STRING);
    }

    public boolean isWhitePumpkin(ItemStack item) {
        return WHITE_PUMPKIN_ID.equals(idOf(item));
    }

    public FlintVariant flintVariantOf(ItemStack item) {
        String id = idOf(item);
        return id == null ? null : FlintVariant.byId(id);
    }

    // ------------------------------------------------------------------ helpers

    /** Legacy colour codes, basic font, italics explicitly off. */
    private static void name(ItemMeta meta, String legacy) {
        Component component = LegacyComponentSerializer.legacySection()
                .deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(component);
    }

    private static void modelData(ItemMeta meta, int value) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
        // Legacy integer field too, so older pack predicates and other plugins still match.
        meta.setCustomModelData(value);
    }

    /** No enchantment text, no attribute text, no "Unbreakable", no extra tooltip lines. */
    private static void hideEverything(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_STORED_ENCHANTS
        );
    }

    private void tag(ItemMeta meta, String id) {
        meta.getPersistentDataContainer().set(keys.itemId, PersistentDataType.STRING, id);
    }
}
