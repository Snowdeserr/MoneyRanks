package me.skythebuilder.moneyranksystem;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class TierListPlugin extends JavaPlugin {

    private Map<String, Integer> rankRequirements;
    private Map<String, String> rankPrefixes;
    private Economy economy;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault not found or no economy plugin hooked. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfigValues();


        new TierListPlaceholderExpansion(this).register();
        getCommand("checkmoney").setExecutor(new TierListCommand(this));
        getCommand("tierlistreload").setExecutor(new ReloadCommand(this));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadConfigValues() {
        rankRequirements = new HashMap<>();
        rankPrefixes = new HashMap<>();

        FileConfiguration config = getConfig();
        config.getConfigurationSection("ranks").getKeys(false).forEach(rank -> {
            double moneyRequired = config.getDouble("ranks." + rank + ".moneyRequired", 0);
            String prefix = config.getString("ranks." + rank + ".prefix", "");

            rankRequirements.put(rank, (int) moneyRequired);
            rankPrefixes.put(rank, ChatColor.translateAlternateColorCodes('&', prefix));
        });
    }

    public Map<String, Integer> getRankRequirements() {
        return rankRequirements;
    }

    public int getMoneyRequired(String rank) {
        return rankRequirements.getOrDefault(rank, 0);
    }

    public String getPrefix(String rank) {
        return rankPrefixes.getOrDefault(rank, "");
    }

    public void reloadConfigValues() {
        reloadConfig();
        loadConfigValues();
    }

    public Economy getEconomy() {
        return economy;
    }


}


class TierListPlaceholderExpansion extends PlaceholderExpansion {

    private final TierListPlugin plugin;

    TierListPlaceholderExpansion(TierListPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "Snowdesert";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        if (identifier.equals("money")) {
            return getRank(player);
        } else if (identifier.equals("money_prefix")) {
            return getPrefix(player);
        }

        return null;
    }

    private String getRank(Player player) {
        double money = getMoney(player);
        TierListPlugin tierListPlugin = plugin;

        for (Map.Entry<String, Integer> entry : tierListPlugin.getRankRequirements().entrySet()) {
            String rank = entry.getKey();
            int moneyRequired = entry.getValue();

            if (money >= moneyRequired) {
                return rank;
            }
        }

        return "Unranked";
    }

    private String getPrefix(Player player) {
        double money = getMoney(player);
        TierListPlugin tierListPlugin = plugin;

        for (Map.Entry<String, Integer> entry : tierListPlugin.getRankRequirements().entrySet()) {
            String rank = entry.getKey();
            int moneyRequired = entry.getValue();

            if (money >= moneyRequired) {
                return tierListPlugin.getPrefix(rank);
            }
        }

        return "";
    }

    private double getMoney(Player player) {
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            return economy.getBalance(player);
        }
        return 0;
    }
}

class TierListCommand implements CommandExecutor {

    private final TierListPlugin plugin;

    TierListCommand(TierListPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by players.");
            return true;
        }

        Player player = (Player) sender;
        String oldRank = getRank(player);

        if (args.length == 0) {

            sendPlayerMoney(player, player);
        } else if (args.length == 1) {
            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer != null) {
                sendPlayerMoney(player, targetPlayer);
            } else {
                sender.sendMessage(ChatColor.RED + "Player not online or not found.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid usage. Use /checkmoney [player]");
        }


        String newRank = getRank(player);

        return true;
    }

    private void sendPlayerMoney(Player sender, Player targetPlayer) {
        double money = getMoney(targetPlayer);
        String rank = getRank(targetPlayer);
        String prefix = getPrefix(targetPlayer);

        sender.sendMessage(ChatColor.GREEN + "Player " + targetPlayer.getName() + " has $" + money + " and the rank: " + prefix);
    }

    private String getRank(Player player) {
        TierListPlugin tierListPlugin = plugin;
        double money = getMoney(player);

        for (Map.Entry<String, Integer> entry : tierListPlugin.getRankRequirements().entrySet()) {
            String rank = entry.getKey();
            int moneyRequired = entry.getValue();

            if (money >= moneyRequired) {
                return rank;
            }
        }

        return "Unranked";
    }

    private String getPrefix(Player player) {
        TierListPlugin tierListPlugin = plugin;
        double money = getMoney(player);

        for (Map.Entry<String, Integer> entry : tierListPlugin.getRankRequirements().entrySet()) {
            String rank = entry.getKey();
            int moneyRequired = entry.getValue();

            if (money >= moneyRequired) {
                return tierListPlugin.getPrefix(rank);
            }
        }

        return "";
    }

    private double getMoney(Player player) {
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            return economy.getBalance(player);
        }
        return 0;
    }
}


class ReloadCommand implements CommandExecutor {

    private final TierListPlugin plugin;

    ReloadCommand(TierListPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("tierlist.reload")) {
            plugin.reloadConfigValues();
            sender.sendMessage(ChatColor.GREEN + "TierList config reloaded!");
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission for this command.");
        }

        return true;
    }
}
