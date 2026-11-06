package org.strayzii.strayMoney;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class PlayerCommands implements TabExecutor {
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("reload", "give", "set", "take");
    private static final long MAX_INPUT = 1_000_000_000_000_000L;

    private final StrayMoney plugin;

    public PlayerCommands(StrayMoney plugin) {
        this.plugin = plugin;
    }

    public @Nullable Player getPlayerByString(String playerName) {
        return Bukkit.getPlayer(playerName);
    }

    private @Nullable OfflinePlayer getKnownPlayer(String playerName) {
        Player online = Bukkit.getPlayer(playerName);
        if (online != null) {
            return online;
        }

        return Bukkit.getOfflinePlayerIfCached(playerName);
    }

    private long maxPay() {
        return plugin.getConfig().getLong("max-pay-amount", -1);
    }

    private long maxBalance() {
        return plugin.getConfig().getLong("max-balance", -1);
    }

    private boolean exceeds(long limit, long value) {
        return limit >= 0 && value > limit;
    }

    private @Nullable Long parseAmount(CommandSender sender, String input, boolean allowZero) {
        long amount;
        try {
            amount = Long.parseLong(input);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, "invalid-number");
            return null;
        }

        if (amount > MAX_INPUT) {
            plugin.sendMessage(sender, "invalid-number");
            return null;
        }

        if (amount < 0 || (amount == 0 && !allowZero)) {
            plugin.sendMessage(sender, "need-positive-amount");
            return null;
        }

        return amount;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "straymoney" -> handleAdmin(commandSender, strings);
            case "straypay" -> handlePay(commandSender, strings);
            case "straybalance" -> handleBalance(commandSender, strings);
        }

        return true;
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("straymoney.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length == 0) {
            plugin.sendMessage(sender, "usage-admin");
            return;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.sendMessage(sender, "reload");
            }
            case "give", "take", "set" -> handleModifyBalance(sender, subCommand, args);
            default -> plugin.sendMessage(sender, "usage-admin");
        }
    }

    private void handleModifyBalance(CommandSender sender, String subCommand, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, "usage-admin");
            return;
        }

        OfflinePlayer target = getKnownPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, "player-not-found");
            return;
        }

        Long amount = parseAmount(sender, args[2], subCommand.equals("set"));
        if (amount == null) {
            return;
        }

        long maxBalance = maxBalance();

        try {
            DatabaseManager db = plugin.getDatabaseManager();
            long balance = db.getMoney(target.getUniqueId());

            switch (subCommand) {
                case "give" -> {
                    if (exceeds(maxBalance, balance + amount)) {
                        plugin.sendMessage(sender, "balance-limit", "$limit", String.valueOf(maxBalance));
                        return;
                    }
                    db.addMoney(target.getUniqueId(), amount);
                }
                case "take" -> {
                    if (balance < amount) {
                        plugin.sendMessage(sender, "not-enough-money");
                        return;
                    }
                    db.takeMoney(target.getUniqueId(), amount);
                }
                case "set" -> {
                    if (exceeds(maxBalance, amount)) {
                        plugin.sendMessage(sender, "balance-limit", "$limit", String.valueOf(maxBalance));
                        return;
                    }
                    db.setMoney(target.getUniqueId(), amount);
                }
            }

            plugin.sendMessage(sender, "success-" + subCommand);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute /straymoney " + subCommand, e);
            plugin.sendMessage(sender, "fail-" + subCommand);
        }
    }

    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "player-only");
            return;
        }

        if (!player.hasPermission("straymoney.pay")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, "usage-pay");
            return;
        }

        Player target = getPlayerByString(args[0]);
        if (target == null) {
            plugin.sendMessage(sender, "player-not-found");
            return;
        }

        if (target.equals(player)) {
            plugin.sendMessage(sender, "cant-pay-yourself");
            return;
        }

        Long amount = parseAmount(sender, args[1], false);
        if (amount == null) {
            return;
        }

        long maxPay = maxPay();
        if (exceeds(maxPay, amount)) {
            plugin.sendMessage(sender, "pay-limit", "$limit", String.valueOf(maxPay));
            return;
        }

        long maxBalance = maxBalance();

        try {
            DatabaseManager db = plugin.getDatabaseManager();

            if (exceeds(maxBalance, db.getMoney(target.getUniqueId()) + amount)) {
                plugin.sendMessage(sender, "balance-limit", "$limit", String.valueOf(maxBalance));
                return;
            }

            if (!db.transfer(player.getUniqueId(), target.getUniqueId(), amount)) {
                plugin.sendMessage(sender, "not-enough-money");
                return;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute /pay", e);
            plugin.sendMessage(sender, "fail-pay");
            return;
        }

        plugin.sendMessage(player, "pay-payer", "$money", String.valueOf(amount), "$player", target.getName());
        plugin.sendMessage(target, "pay-receiver", "$money", String.valueOf(amount), "$player", player.getName());
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("straymoney.balance")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length > 1) {
            plugin.sendMessage(sender, "usage-balance");
            return;
        }

        try {
            if (args.length == 1) {
                if (!sender.hasPermission("straymoney.balance.others")) {
                    plugin.sendMessage(sender, "no-permission");
                    return;
                }

                OfflinePlayer target = getKnownPlayer(args[0]);
                if (target == null) {
                    plugin.sendMessage(sender, "player-not-found");
                    return;
                }

                long balance = plugin.getDatabaseManager().getMoney(target.getUniqueId());
                String targetName = target.getName() != null ? target.getName() : args[0];
                plugin.sendMessage(sender, "balance-others", "$balance", String.valueOf(balance), "$player", targetName);
            } else {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, "player-only");
                    return;
                }

                long balance = plugin.getDatabaseManager().getMoney(player.getUniqueId());
                plugin.sendMessage(sender, "balance-self", "$balance", String.valueOf(balance));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute /balance", e);
            plugin.sendMessage(sender, "fail-balance");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "straymoney" -> {
                if (!commandSender.hasPermission("straymoney.admin")) {
                    return List.of();
                }

                if (strings.length == 1) {
                    return filter(ADMIN_SUBCOMMANDS, strings[0]);
                }

                if (strings.length == 2 && !strings[0].equalsIgnoreCase("reload")) {
                    return filter(onlineNames(commandSender, false), strings[1]);
                }

                return List.of();
            }
            case "straypay" -> {
                if (!commandSender.hasPermission("straymoney.pay")) {
                    return List.of();
                }

                if (strings.length == 1) {
                    return filter(onlineNames(commandSender, true), strings[0]);
                }

                return List.of();
            }
            case "straybalance" -> {
                if (!commandSender.hasPermission("straymoney.balance.others")) {
                    return List.of();
                }

                if (strings.length == 1) {
                    return filter(onlineNames(commandSender, false), strings[0]);
                }

                return List.of();
            }
        }

        return List.of();
    }

    private List<String> onlineNames(CommandSender sender, boolean excludeSelf) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (excludeSelf && online.equals(sender)) {
                continue;
            }
            names.add(online.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
