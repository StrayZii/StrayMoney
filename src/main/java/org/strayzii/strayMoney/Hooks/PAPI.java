package org.strayzii.strayMoney.Hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.strayzii.strayMoney.Placeholder;
import org.strayzii.strayMoney.StrayMoney;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class PAPI extends PlaceholderExpansion {
    private final StrayMoney plugin;

    private final Map<String, Placeholder> placeholders = new HashMap<>();

    public PAPI(StrayMoney plugin) {
        this.plugin = plugin;

        placeholders.put("balance", new Placeholder() {
            @Override
            public String run(Player p, String identifier) {
                try {
                    return String.valueOf(plugin.getDatabaseManager().getMoney(p.getUniqueId()));
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get balance for placeholder", e);
                    return "0";
                }
            }
        });
        placeholders.put("balance_formatted", new Placeholder() {
            @Override
            public String run(Player p, String identifier) {
                try {
                    double balance = plugin.getDatabaseManager().getMoney(p.getUniqueId());

                    String[] suffixes = {
                            "", "к", "млн", "млрд", "трлн", "квдрлн", "квнтлн"
                    };

                    int index = 0;

                    while (balance >= 1000 && index < suffixes.length - 1) {
                        balance /= 1000;
                        index++;
                    }

                    if (index == 0) {
                        return String.valueOf((long) balance);
                    }

                    // Два знака после запятой, но убираем лишние нули
                    String formatted = String.format(
                            java.util.Locale.US, "%.2f", balance
                    );

                    formatted = formatted.replaceAll("\\.?0+$", "");

                    return formatted + suffixes[index];

                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to get balance for placeholder", e);
                    return "0";
                }
            }
        });
    }

    @Override
    public @NotNull String getIdentifier() {
        return "straymoney";
    }

    @Override
    public @NotNull String getAuthor() {
        return "StrayZii";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    public String process(Player p, String identifier) {
        Placeholder placeholder = placeholders.get(identifier);
        if (placeholder == null) {
            return null;
        }
        return placeholder.run(p, identifier);
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        return process(player, params);
    }
}
