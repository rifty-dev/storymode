# StoryModeItems — Paper 1.21.11

Plugin + resource pack recreating three Minecraft: Story Mode items.

```
StoryModeItems/
├── pom.xml
├── src/main/java/dev/storymode/
│   ├── StoryModePlugin.java   main class, /storyitem command
│   ├── Keys.java              NamespacedKeys (attribute modifiers, PDC, cooldown groups)
│   ├── StoryItems.java        item factory + identification
│   ├── FlintVariant.java      all blue-vs-green differences in one place
│   ├── FlintListener.java     catalyst, fire colouring, melee, contact debuffs
│   ├── PumpkinListener.java   stalker passives, axe precision, wear, cracking
│   └── FireTracker.java       which flame came from which tool
├── src/main/resources/plugin.yml
└── resourcepack/              drop-in pack (your four textures, sliced and converted)
```

Build with `mvn package`, drop the jar in `plugins/`, zip `resourcepack/` and serve it.
Get items with `/storyitem white_pumpkin | flint_blue | flint_green [player]`.

## Textures

`white_pumpkin_mask.png` and `broken_mask.png` were 64×64 block atlases, not single item
sprites — they contain a carved face tile, side tiles, and a stem/top tile. I sliced them into
`white_pumpkin_front`, `white_pumpkin_front_cracked`, `white_pumpkin_side` and
`white_pumpkin_top`, and the models use `block/orientable` so the pumpkin still renders as a
proper 3D block on the head. The two flint webp files were downscaled 160×160 → 16×16 PNG, which
came out pixel-clean.

`pack_format` is set to 75 with a `supported_formats` range — worth double-checking against the
wiki for your exact build, since a wrong number only produces the "incompatible" warning.

## Things in the brief that don't work as written

**The black pumpkin overlay is client-side.** No server API can suppress it. It's handled in the
pack instead: `textures/misc/pumpkinblur.png` is now a 1×1 transparent PNG, which removes the
overlay entirely for anyone with the pack loaded.

**Footstep sounds are client-side too.** Each client plays its own step sounds from its own
movement prediction; the server never sends those packets, so there is nothing to cancel through
the Bukkit API. This part of Faceless Stalker is not implemented. Getting it would need packet
interception (ProtocolLib/PacketEvents) for *other* players' steps, and it still wouldn't
silence the wearer's own footsteps on their own client.

**Fire Aspect V vs. "hits apply ONLY Weakness".** These contradict each other — Fire Aspect's
whole job is igniting the target. The enchantment stays on the item as specified, and
`FlintListener.NEUTRALISE_FIRE_ASPECT_ON_HIT` cancels the ignition it causes so only Weakness
lands. Flip that constant to `false` if you'd rather the enchantment actually burn.

**Soul fire can't survive everywhere.** Vanilla `soul_fire` only persists above blocks in
`#minecraft:soul_fire_base_blocks` (soul sand, soul soil). The plugin writes it with
`setBlockData(data, false)` to skip the physics check, which makes it stick on ordinary blocks —
but a later neighbour update can still remove it. `FireTracker`'s sweeper prunes the bookkeeping
when that happens. If you need guaranteed persistence anywhere, the alternative is a datapack
adding your blocks to that tag.

**"+1 Knockback Resistance"** is 100% knockback immunity, not a netherite helmet's value — a real
netherite helmet grants `0.10`. I implemented the literal `1.0` you asked for;
`StoryItems.PUMPKIN_KNOCKBACK_RESISTANCE` is a one-line change for vanilla parity.

**The client's ~4-tick right-click repeat limit** can't be removed server-side, so "rapid fire"
means the plugin adds no delay of its own: ignition rides on vanilla's own handler (which keeps
TNT, campfires, candles and nether portals working for free) and no use-cooldown is applied to
placement.

## Implementation notes worth knowing

- **Durability on a carved pumpkin.** The `max_damage` component requires `max_stack_size: 1`, so
  the item is set unstackable — without that the client rejects the component. Carved pumpkins
  also aren't armour, so they never take damage naturally; `PumpkinListener.onWearerDamaged`
  mimics vanilla armour wear (`max(1, damage/4)`, with the Unbreaking III avoidance roll of
  `0.6 + 0.4/(level+1)`) so the cracked texture actually becomes reachable. Remove that handler
  if you want the pumpkin to be effectively permanent.
- **Cracking** is plugin-driven, as requested: `syncModel` swaps CustomModelData 1001 ↔ 1002 at
  100 remaining durability, and it's reversible on repair. A `range_dispatch` on
  `minecraft:damage` in the pack would do the same thing with no plugin involvement if you'd
  rather move it client-side.
- **Axe Precision** reads the player's live `ATTACK_SPEED` value (which already includes the held
  axe's own modifier) and adds a transient modifier for the gap up to 1.6. That means it works
  for every axe tier automatically instead of hard-coding wood/stone/iron/diamond values.
- **Cooldowns** use the `use_cooldown` component with a per-variant cooldown group, so blue,
  green and vanilla flint and steel don't share one timer — which they would if this used the
  old `setCooldown(Material, ticks)`.
- **Nametag hiding** uses a scoreboard team (`mcsm_faceless`) with `NAME_TAG_VISIBILITY: NEVER`.
  If you already manage teams for prefixes/colours, this will fight with that — say the word and
  I'll switch it to per-player packet-level hiding instead.
- **No chat output at all** from any ability, and no lore anywhere; `HIDE_ATTRIBUTES`,
  `HIDE_ENCHANTS`, `HIDE_UNBREAKABLE` and `HIDE_ADDITIONAL_TOOLTIP` keep the tooltips to just the
  coloured name, and names are deserialized from your `§` codes with italics explicitly disabled
  so they don't render slanted.
- Fire contact debuffs deliberately only trigger for flames this plugin tracked, so natural fire
  and other players' vanilla flint and steel behave normally.

## Untested API surface

I couldn't compile against a real 1.21.11 jar here. The pieces most worth a smoke test are
`CustomModelDataComponent#setFloats`, `UseCooldownComponent`, `HumanEntity#setCooldown(ItemStack, int)`,
and the `Attribute.ARMOR`-style enum names (renamed from `GENERIC_ARMOR` in 1.21.3).
