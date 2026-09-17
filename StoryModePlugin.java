package dev.storymode;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StoryModePlugin extends JavaPlugin implements TabCompleter {

    private Keys keys;
    private StoryItems items;
    private FireTracker fireTracker;
    private PumpkinListener pumpkinListener;

    @Override
    public void onEnable() {
        this.keys = new Keys(this);
        this.items = new StoryItems(keys);

        this.fireTracker = new FireTracker(this);
        this.fireTracker.start();

        this.pumpkinListener = new PumpkinListener(this, keys, items);
        this.pumpkinListener.start();

        getServer().getPluginManager().registerEvents(pumpkinListener, this);
        getServer().getPluginManager().registerEvents(
                new FlintListener(this, items, fireTracker), this);

        // Keeps stored (not worn) White Pumpkins showing the right cracked/pristine model.
        getServer().getScheduler().runTaskTimer(this, this::syncStoredPumpkins, 60L, 40L);

        if (getCommand("storyitem") != null) {
            getCommand("storyitem").setExecutor(this::handleGive);
            getCommand("storyitem").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (pumpkinListener != null) pumpkinListener.shutdown();
        if (fireTracker != null) fireTracker.stop();
    }

    private void syncStoredPumpkins() {
        for (Player player : getServer().getOnlinePlayers()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                pumpkinListener.syncModel(stack);
            }
        }
    }

    // ------------------------------------------------------------------ command

    private boolean handleGive(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <white_pumpkin|flint_blue|flint_green> [player]");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = getServer().getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Console must name a player.");
            return true;
        }
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        String id = args[0].toLowerCase(Locale.ROOT);
        ItemStack stack = switch (id) {
            case StoryItems.WHITE_PUMPKIN_ID -> items.whitePumpkin();
            case "flint_blue" -> items.flint(FlintVariant.BLUE);
            case "flint_green" -> items.flint(FlintVariant.GREEN);
            default -> null;
        };
        if (stack == null) {
            sender.sendMessage("Unknown item: " + id);
            return true;
        }

        target.getInventory().addItem(stack).forEach((slot, leftover) ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String id : List.of(StoryItems.WHITE_PUMPKIN_ID, "flint_blue", "flint_green")) {
                if (id.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(id);
            }
        } else if (args.length == 2) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }

    public StoryItems items() {
        return items;
    }
}
