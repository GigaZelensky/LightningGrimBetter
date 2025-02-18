package ac.grim.grimac;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class TridentDupeFix implements Listener {

   @EventHandler(ignoreCancelled = true)
   public void onInventoryClick(InventoryClickEvent event) {
      HumanEntity clicker = event.getWhoClicked();
      if (clicker instanceof Player) {
         Player player = (Player) clicker;
         if (isChargingTrident(player)) {
            event.setCancelled(true);
         }
      }
   }

   private boolean isChargingTrident(Player player) {
      ItemStack mainHand = player.getInventory().getItemInMainHand();
      return mainHand != null && mainHand.getType() == Material.TRIDENT ? player.isHandRaised() : false;
   }
}
