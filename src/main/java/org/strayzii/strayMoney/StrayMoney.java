package org.strayzii.strayMoney;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.strayzii.strayMoney.Hooks.PAPI;
import org.strayzii.strayMoney.Listeners.DatabaseRegisterer;

import java.util.List;

public final class StrayMoney extends JavaPlugin {
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        getServer().getPluginManager().registerEvents(new DatabaseRegisterer(this), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PAPI(this).register();
        }

        PlayerCommands commands = new PlayerCommands(this);
        for (String name : List.of("straymoney", "straypay", "straybalance")) {
            getCommand(name).setExecutor(commands);
            getCommand(name).setTabCompleter(commands);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Component component(String path, String... replacements) {
        String msg = getConfig().getString("messages." + path);
        if (msg == null || msg.isEmpty()) return null;

        msg = msg.replace("$prefix", getConfig().getString("messages.prefix", ""));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return MiniMessage.miniMessage().deserialize(msg);
    }

    public void sendMessage(CommandSender target, String path, String... replacements) {
        Component c = component(path, replacements);
        if (c != null) target.sendMessage(c);
    }

    public void announce(String path, String... replacements) {
        Component c = component(path, replacements);
        if (c != null) Bukkit.broadcast(c);
    }
}
