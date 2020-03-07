package xyz.rodeldev.youitime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import xyz.rodeldev.XMaterial;
import xyz.rodeldev.inventory.CustomMenu;
import xyz.rodeldev.inventory.PlaceholderInstance;
import xyz.rodeldev.templates.Placeholder;
import xyz.rodeldev.templates.Template;
import xyz.rodeldev.templates.TemplateRegistry;

public class YouiTimePlugin extends JavaPlugin implements Listener {
    private List<Integer> current_time_slots = new ArrayList<>();
    private List<Integer> day_slots = new ArrayList<>();
    private List<Integer> night_slots = new ArrayList<>();
    private Template template;

    private ConcurrentHashMap<UUID, Inventory> uis = new ConcurrentHashMap<>();

    private String time, day, night, changed;

    private CustomMenu menu;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if(getServer().getPluginManager().isPluginEnabled("Youi")){
            // Create the template, defining the plugin owner and name
            template = new Template(this, "time_ui")
            .setInventorySize(9*3, true)
            // This will define the default items will be set when created
            .defaultFillInventory(XMaterial.GRAY_STAINED_GLASS_PANE.parseItem())
            .defaultSetItem(XMaterial.LAVA_BUCKET.parseItem(), 10)
            .defaultSetItem(XMaterial.CLOCK.parseItem(), 11)
            .defaultSetItem(XMaterial.WATER_BUCKET.parseItem(), 12)
            .defaultSetItem(setItemName(XMaterial.GHAST_TEAR.parseItem(), ChatColor.GRAY+"(Custom) Noon"), 15)
            .defaultSetItem(setItemName(XMaterial.MAGMA_CREAM.parseItem(), ChatColor.RED+"(Custom) Sunrise"), 16)
            // Register the placeholder with their descriptions
            .registerPlaceholders(
                new Placeholder("current_time").setDescription("This item will change name to the current time"),
                new Placeholder("day_button").setDescription("When clicked, the time will be changed to day"),
                new Placeholder("night_button").setDescription("When clicked, the time will be changed to night")
            )
            // Set placeholder to some slots (note that there are they match with the items!)
            .defaultAddPlaceholder("day_button", 10)
            .defaultAddPlaceholder("current_time", 11)
            .defaultAddPlaceholder("night_button", 12)
            .defaultAddPlaceholder("custom{\"ticks\":6000}", 15)
            .defaultAddPlaceholder("custom{\"ticks\":23000}", 16);
            // Finally register the placeholder into Youi
            TemplateRegistry.register(template);
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for(Inventory inventory : uis.values()){
                if(!inventory.getViewers().isEmpty()){
                    setTimeItems(inventory);
                }
            }
        }, 0, 10);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)){
            sender.sendMessage("Only for players!");
            return true;
        }

        saveDefaultConfig();

        FileConfiguration config = getConfig();
        time = ChatColor.translateAlternateColorCodes('&', config.getString("time"));
        day = ChatColor.translateAlternateColorCodes('&', config.getString("day"));
        night = ChatColor.translateAlternateColorCodes('&', config.getString("night"));
        changed = ChatColor.translateAlternateColorCodes('&', config.getString("changed"));

        openInventory((Player) sender);
        return true;
    }

    public void openInventory(Player player){
        Inventory ui;

        // We try to use a custom menu or the default we defined in the onEnabled method
        if(template!=null && (menu = template.getOverride(true))!=null){
            ui = menu.getBukkitInventory();
            // Save all slots containing placeholders
            current_time_slots = menu.slotsWithPlaceholder("current_time");
            night_slots = menu.slotsWithPlaceholder("night_button");
            day_slots = menu.slotsWithPlaceholder("day_button");
        }else{
            // Otherwise, we create the a bukkit one by ourselfs
            ui = Bukkit.createInventory(null, 9*3);
            for(int i = 0; i < ui.getSize(); i++){
                ui.setItem(i, new ItemStack(XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()));
            }

            // Since we don't have placeholders, we are going to save only the hardcoded slot
            // We are saving so we don't have to do the same name replacing twice, check the next comment
            ui.setItem(13, XMaterial.CLOCK.parseItem());
            current_time_slots.clear();
            current_time_slots.add(13);
            
            ui.setItem(11, XMaterial.LAVA_BUCKET.parseItem());
            day_slots.clear();
            day_slots.add(11);
            
            ui.setItem(15, XMaterial.WATER_BUCKET.parseItem());
            night_slots.clear();
            night_slots.add(15);
        }

        day_slots.stream().map(slot -> ui.getItem(slot)).forEach(item -> {
            setItemName(item, day);
        });

        night_slots.stream().map(slot -> ui.getItem(slot)).forEach(item -> {
            setItemName(item, night);
        });

        player.openInventory(ui);
        setTimeItems(ui);

        uis.put(player.getUniqueId(), ui);
    }

    public void setTimeItems(Inventory ui){
        current_time_slots.stream().map(slot -> ui.getItem(slot)).forEach(item -> {
            setItemName(item, time.replace("{ticks}", String.valueOf(ui.getViewers().get(0).getWorld().getTime())));
        });
    }

    public ItemStack setItemName(ItemStack item, String name){
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryInteract(InventoryClickEvent e){
        Inventory ui;
        if((ui = uis.get(e.getWhoClicked().getUniqueId()))!=null && e.getInventory().equals(ui)){
            e.setCancelled(true);

            HumanEntity player = e.getWhoClicked();
            World world = player.getWorld();

            for(PlaceholderInstance placeholder : menu.getPlaceholdersIn(e.getSlot())){
                if(placeholder.getPlaceholderName().equals("custom")){
                    world.setTime(placeholder.getPlaceholderData().get().getAsJsonObject().get("ticks").getAsLong());
                }
            }

            if(day_slots.contains(e.getSlot())){
                world.setTime(6000);
                player.sendMessage(changed);
            }
            if(night_slots.contains(e.getSlot())){
                world.setTime(18000);
                player.sendMessage(changed);
            }
        }
    }
}