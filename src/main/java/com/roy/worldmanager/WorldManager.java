
package com.roy.worldmanager;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class WorldManager extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<String, WorldData> worlds = new HashMap<>();
    private final Map<UUID, Integer> limits = new HashMap<>();

    private static final String MENU = ChatColor.DARK_AQUA + "World Manager";
    private static final String CREATE = ChatColor.DARK_GREEN + "Create World";
    private static final String MY_WORLDS = ChatColor.DARK_BLUE + "My Worlds";
    private static final String INVITES = ChatColor.DARK_PURPLE + "Invitations";

    static class WorldData {
        String name;
        String type;
        UUID owner;
        boolean isPublic = false;
        Set<UUID> invited = new HashSet<>();

        WorldData(String name, String type, UUID owner) {
            this.name = name;
            this.type = type;
            this.owner = owner;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        getCommand("worlds").setExecutor(this);
        getCommand("worldadmin").setExecutor(this);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("WorldManager enabled!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private int limit(UUID id) {
        return limits.getOrDefault(
            id,
            getConfig().getInt("default-world-limit", 5)
        );
    }

    private long owned(UUID id) {
        return worlds.values().stream()
            .filter(w -> w.owner.equals(id))
            .count();
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + name);

        meta.setLore(Arrays.stream(lore)
            .map(s -> ChatColor.GRAY + s)
            .toList());

        stack.setItemMeta(meta);
        return stack;
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU);

        inv.setItem(10, item(
            Material.GRASS_BLOCK,
            "Create World"
        ));

        inv.setItem(12, item(
            Material.BOOK,
            "My Worlds"
        ));

        inv.setItem(14, item(
            Material.WRITABLE_BOOK,
            "Invitations"
        ));

        inv.setItem(16, item(
            Material.COMPASS,
            "Return to Lobby"
        ));

        inv.setItem(22, item(
            Material.PAPER,
            "Worlds: " + owned(p.getUniqueId()) + "/" +
                limit(p.getUniqueId())
        ));

        p.openInventory(inv);
    }

    private void openCreate(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, CREATE);

        inv.setItem(10, item(
            Material.OAK_SAPLING,
            "Survival"
        ));

        inv.setItem(12, item(
            Material.DIAMOND_BLOCK,
            "Creative"
        ));

        inv.setItem(14, item(
            Material.GRASS_BLOCK,
            "Flat"
        ));

        inv.setItem(16, item(
            Material.LIGHT_BLUE_STAINED_GLASS,
            "Void"
        ));

        p.openInventory(inv);
    }

    private void openMyWorlds(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, MY_WORLDS);

        int slot = 0;

        for (WorldData w : worlds.values()) {
            if (!w.owner.equals(p.getUniqueId())) continue;
            if (slot >= 45) break;

            inv.setItem(slot++, item(
                Material.GRASS_BLOCK,
                w.name,
                "Type: " + w.type,
                "Access: " + (w.isPublic ? "Public" : "Private"),
                "Click to enter"
            ));
        }

        inv.setItem(49, item(Material.ARROW, "Back"));
        p.openInventory(inv);
    }

    private void openInvites(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, INVITES);

        int slot = 0;

        for (WorldData w : worlds.values()) {
            if (!w.invited.contains(p.getUniqueId())) continue;
            if (slot >= 45) break;

            inv.setItem(slot++, item(
                Material.ENDER_EYE,
                w.name,
                "Click to accept and enter"
            ));
        }

        inv.setItem(49, item(Material.ARROW, "Back"));
        p.openInventory(inv);
    }

    private String newWorldName(Player p) {
        String prefix = p.getUniqueId()
            .toString().replace("-", "").substring(0, 8);

        int number = 1;
        String name;

        do {
            name = "wm_" + prefix + "_" + number++;
        } while (worlds.containsKey(name) ||
                 new File(Bukkit.getWorldContainer(), name).exists());

        return name;
    }

    private void createWorld(Player p, String type) {
        if (owned(p.getUniqueId()) >= limit(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "You reached your world limit!");
            return;
        }

        String name = newWorldName(p);
        WorldCreator creator = new WorldCreator(name);

        switch (type) {
            case "Flat" -> creator.type(WorldType.FLAT);
            case "Void" -> creator.generator(new VoidGenerator());
            default -> creator.type(WorldType.NORMAL);
        }

        World world = Bukkit.createWorld(creator);

        if (world == null) {
            p.sendMessage(ChatColor.RED + "World creation failed.");
            return;
        }

        if (type.equals("Void")) {
            int y = 80;

            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.getBlockAt(x, y - 1, z).setType(
                        x == 0 && z == 0
                            ? Material.STONE
                            : Material.GLASS
                    );
                }
            }

            world.setSpawnLocation(0, y, 0);
        }

        WorldData data = new WorldData(
            name,
            type,
            p.getUniqueId()
        );

        worlds.put(name, data);
        saveData();

        p.closeInventory();
        p.teleport(world.getSpawnLocation());

        if (type.equals("Creative")) {
            p.setGameMode(GameMode.CREATIVE);
        } else {
            p.setGameMode(GameMode.SURVIVAL);
        }

        p.sendMessage(ChatColor.GREEN +
            "Your private world has been created!");
    }

    private static class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(
            World world,
            Random random,
            int x,
            int z,
            BiomeGrid biome
        ) {
            return createChunkData(world);
        }
    }

    private void enterWorld(Player p, WorldData data) {
        if (!data.isPublic &&
            !data.owner.equals(p.getUniqueId()) &&
            !data.invited.contains(p.getUniqueId())) {

            p.sendMessage(ChatColor.RED +
                "You are not invited to this private world!");
            return;
        }

        World world = Bukkit.getWorld(data.name);

        if (world == null) {
            WorldCreator creator = new WorldCreator(data.name);

            if (data.type.equals("Flat")) {
                creator.type(WorldType.FLAT);
            } else if (data.type.equals("Void")) {
                creator.generator(new VoidGenerator());
            }

            world = Bukkit.createWorld(creator);
        }

        if (world == null) {
            p.sendMessage(ChatColor.RED + "World unavailable.");
            return;
        }

        p.teleport(world.getSpawnLocation());

        if (data.type.equals("Creative")) {
            p.setGameMode(GameMode.CREATIVE);
        } else {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void lobby(Player p) {
        String name = getConfig().getString("lobby-world", "world");
        World world = Bukkit.getWorld(name);

        if (world == null) {
            p.sendMessage(ChatColor.RED +
                "Lobby world not found: " + name);
            return;
        }

        p.teleport(world.getSpawnLocation());
        p.setGameMode(GameMode.SURVIVAL);
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (command.getName().equalsIgnoreCase("worldadmin")) {
            if (!sender.hasPermission("worldmanager.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length == 3 &&
                args[0].equalsIgnoreCase("limit")) {

                try {
                    OfflinePlayer target =
                        Bukkit.getOfflinePlayer(args[1]);

                    int value = Integer.parseInt(args[2]);

                    if (value < 0 || value > 100) {
                        throw new NumberFormatException();
                    }

                    limits.put(target.getUniqueId(), value);
                    saveData();

                    sender.sendMessage(ChatColor.GREEN +
                        "World limit updated.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED +
                        "Enter a number from 0 to 100.");
                }

                return true;
            }

            sender.sendMessage(
                "/worldadmin limit <player> <number>"
            );
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            openMenu(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "lobby" -> lobby(p);

            case "invite" -> {
                if (args.length < 2) {
                    p.sendMessage("/worlds invite <player>");
                    return true;
                }

                WorldData data = worlds.values().stream()
                    .filter(w -> w.owner.equals(p.getUniqueId()))
                    .reduce((a, b) -> b)
                    .orElse(null);

                if (data == null) {
                    p.sendMessage(ChatColor.RED +
                        "Create a world first.");
                    return true;
                }

                OfflinePlayer target =
                    Bukkit.getOfflinePlayer(args[1]);

                data.invited.add(target.getUniqueId());
                saveData();

                p.sendMessage(ChatColor.GREEN +
                    "Invitation sent.");

                Player online = Bukkit.getPlayer(target.getUniqueId());

                if (online != null) {
                    online.sendMessage(ChatColor.GREEN +
                        p.getName() + " invited you to " +
                        data.name + ". Use /worlds.");
                }
            }

            case "accept" -> {
                if (args.length < 2) {
                    p.sendMessage("/worlds accept <owner>");
                    return true;
                }

                UUID owner =
                    Bukkit.getOfflinePlayer(args[1]).getUniqueId();

                WorldData data = worlds.values().stream()
                    .filter(w -> w.owner.equals(owner))
                    .filter(w -> w.invited.contains(p.getUniqueId()))
                    .findFirst().orElse(null);

                if (data == null) {
                    p.sendMessage(ChatColor.RED +
                        "No invitation found.");
                } else {
                    data.invited.remove(p.getUniqueId());
                    saveData();
                    enterWorld(p, data);
                }
            }

            case "decline" -> {
                if (args.length < 2) {
                    p.sendMessage("/worlds decline <owner>");
                    return true;
                }

                UUID owner =
                    Bukkit.getOfflinePlayer(args[1]).getUniqueId();

                for (WorldData w : worlds.values()) {
                    if (w.owner.equals(owner)) {
                        w.invited.remove(p.getUniqueId());
                    }
                }

                saveData();
                p.sendMessage(ChatColor.GREEN +
                    "Invitation declined.");
            }

            case "public", "private" -> {
                if (args.length < 2) {
                    p.sendMessage("/worlds public|private <world>");
                    return true;
                }

                WorldData data = worlds.get(args[1]);

                if (data == null ||
                    !data.owner.equals(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED +
                        "World not found or not yours.");
                } else {
                    data.isPublic =
                        args[0].equalsIgnoreCase("public");

                    saveData();

                    p.sendMessage(ChatColor.GREEN +
                        "World visibility updated.");
                }
            }

            default -> p.sendMessage(
                "Use /worlds, /worlds invite, /worlds accept, " +
                "/worlds decline, /worlds public, /worlds private, " +
                "or /worlds lobby."
            );
        }

        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();

        if (!title.equals(MENU) &&
            !title.equals(CREATE) &&
            !title.equals(MY_WORLDS) &&
            !title.equals(INVITES)) return;

        e.setCancelled(true);

        if (e.getCurrentItem() == null ||
            !e.getCurrentItem().hasItemMeta()) return;

        String name = ChatColor.stripColor(
            e.getCurrentItem().getItemMeta().getDisplayName()
        );

        if (title.equals(MENU)) {
            switch (name) {
                case "Create World" -> openCreate(p);
                case "My Worlds" -> openMyWorlds(p);
                case "Invitations" -> openInvites(p);
                case "Return to Lobby" -> {
                    p.closeInventory();
                    lobby(p);
                }
            }
        } else if (title.equals(CREATE)) {
            if (List.of("Survival", "Creative", "Flat", "Void")
                .contains(name)) {
                createWorld(p, name);
            }
        } else if (title.equals(MY_WORLDS)) {
            if (name.equals("Back")) {
                openMenu(p);
                return;
            }

            WorldData data = worlds.get(name);
            if (data != null) enterWorld(p, data);
        } else if (title.equals(INVITES)) {
            if (name.equals("Back")) {
                openMenu(p);
                return;
            }

            for (WorldData data : worlds.values()) {
                if (data.name.equals(name) &&
                    data.invited.contains(p.getUniqueId())) {
                    data.invited.remove(p.getUniqueId());
                    saveData();
                    enterWorld(p, data);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null ||
            e.getTo().getWorld() == null) return;

        WorldData data = worlds.get(
            e.getTo().getWorld().getName()
        );

        if (data == null) return;

        if (!data.isPublic &&
            !data.owner.equals(e.getPlayer().getUniqueId()) &&
            !data.invited.contains(e.getPlayer().getUniqueId())) {

            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED +
                "This world is private.");
        }
    }

    private void loadData() {
        var section = getConfig().getConfigurationSection("worlds");
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            String path = "worlds." + name + ".";

            try {
                WorldData data = new WorldData(
                    name,
                    getConfig().getString(path + "type", "Survival"),
                    UUID.fromString(getConfig().getString(path + "owner"))
                );

                data.isPublic = getConfig().getBoolean(path + "public");

                for (String id : getConfig().getStringList(path + "invited")) {
                    data.invited.add(UUID.fromString(id));
                }

                worlds.put(name, data);
            } catch (Exception ex) {
                getLogger().warning("Could not load world " + name);
            }
        }

        var limitSection = getConfig().getConfigurationSection("limits");

        if (limitSection != null) {
            for (String id : limitSection.getKeys(false)) {
                try {
                    limits.put(UUID.fromString(id),
                        getConfig().getInt("limits." + id));
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveData() {
        getConfig().set("worlds", null);
        getConfig().set("limits", null);

        for (WorldData data : worlds.values()) {
            String path = "worlds." + data.name + ".";

            getConfig().set(path + "type", data.type);
            getConfig().set(path + "owner", data.owner.toString());
            getConfig().set(path + "public", data.isPublic);

            getConfig().set(
                path + "invited",
                data.invited.stream()
                    .map(UUID::toString)
                    .toList()
            );
        }

        for (var entry : limits.entrySet()) {
            getConfig().set(
                "limits." + entry.getKey(),
                entry.getValue()
            );
        }

        saveConfig();
    }
}
