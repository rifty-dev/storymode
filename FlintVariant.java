package dev.storymode;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Everything that differs between the two Enchanted Flint and Steel variants lives here,
 * so the listener itself is variant-agnostic.
 */
public enum FlintVariant {

    BLUE(
            "flint_blue",
            "§bEnchanted Flint and Steel",
            2001,
            Material.SOUL_FIRE,
            /* weaknessOnHitAmplifier  */ 1,   // Weakness II
            /* weaknessInFireAmplifier */ 1,   // Weakness II
            /* catalystDurationTicks   */ 300, // 15s
            Particle.SOUL_FIRE_FLAME,
            Sound.BLOCK_SOUL_SOIL_PLACE
    ) {
        @Override
        public List<PotionEffect> catalystEffects() {
            return List.of(
                    new PotionEffect(PotionEffectType.SPEED, catalystDurationTicks(), 2, true, false, true),
                    new PotionEffect(PotionEffectType.REGENERATION, catalystDurationTicks(), 1, true, false, true),
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, catalystDurationTicks(), 0, true, false, true)
            );
        }
    },

    GREEN(
            "flint_green",
            "§2Enchanted Flint and Steel",
            2002,
            Material.FIRE,
            /* weaknessOnHitAmplifier  */ 0,   // Weakness I
            /* weaknessInFireAmplifier */ 0,   // Weakness I
            /* catalystDurationTicks   */ 200, // 10s
            Particle.FLAME,
            Sound.ITEM_FLINTANDSTEEL_USE
    ) {
        @Override
        public List<PotionEffect> catalystEffects() {
            return List.of(
                    new PotionEffect(PotionEffectType.SPEED, catalystDurationTicks(), 1, true, false, true),
                    new PotionEffect(PotionEffectType.REGENERATION, catalystDurationTicks(), 0, true, false, true),
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, catalystDurationTicks(), 0, true, false, true)
            );
        }
    };

    private final String id;
    private final String displayName;
    private final int modelData;
    private final Material fireMaterial;
    private final int weaknessOnHitAmplifier;
    private final int weaknessInFireAmplifier;
    private final int catalystDurationTicks;
    private final Particle particle;
    private final Sound placeSound;

    FlintVariant(String id, String displayName, int modelData, Material fireMaterial,
                 int weaknessOnHitAmplifier, int weaknessInFireAmplifier,
                 int catalystDurationTicks, Particle particle, Sound placeSound) {
        this.id = id;
        this.displayName = displayName;
        this.modelData = modelData;
        this.fireMaterial = fireMaterial;
        this.weaknessOnHitAmplifier = weaknessOnHitAmplifier;
        this.weaknessInFireAmplifier = weaknessInFireAmplifier;
        this.catalystDurationTicks = catalystDurationTicks;
        this.particle = particle;
        this.placeSound = placeSound;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int modelData() { return modelData; }
    public Material fireMaterial() { return fireMaterial; }
    public int weaknessOnHitAmplifier() { return weaknessOnHitAmplifier; }
    public int weaknessInFireAmplifier() { return weaknessInFireAmplifier; }
    public int catalystDurationTicks() { return catalystDurationTicks; }
    public Particle particle() { return particle; }
    public Sound placeSound() { return placeSound; }

    public abstract List<PotionEffect> catalystEffects();

    /** Debuffs handed to anything standing in fire placed by this tool. */
    public List<PotionEffect> fireContactEffects() {
        return List.of(
                new PotionEffect(PotionEffectType.WEAKNESS, 200, weaknessInFireAmplifier, true, false, true),
                new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, true, false, true) // Slowness II for both
        );
    }

    public static FlintVariant byId(String id) {
        for (FlintVariant v : values()) {
            if (v.id.equals(id)) return v;
        }
        return null;
    }
}
