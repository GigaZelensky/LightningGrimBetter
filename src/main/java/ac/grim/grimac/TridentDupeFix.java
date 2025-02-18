package ac.grim.grimac;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class TridentDupeFix extends JavaPlugin implements Listener {
   public void onEnable() {
      this.getServer().getPluginManager().registerEvents(this, this);
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onInventoryClick(InventoryClickEvent event) {
      HumanEntity var3 = event.getWhoClicked();
      if (var3 instanceof Player) {
         Player player = (Player)var3;
         if (this.isChargingTrident(player)) {
            event.setCancelled(true);
         }

      }
   }

   private boolean isChargingTrident(Player player) {
      ItemStack mainHand = player.getInventory().getItemInMainHand();
      return mainHand != null && mainHand.getType() == Material.TRIDENT ? player.isHandRaised() : false;
   }
}
