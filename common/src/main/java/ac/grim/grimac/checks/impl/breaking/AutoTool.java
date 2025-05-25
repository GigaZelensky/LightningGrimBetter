package ac.grim.grimac.checks.impl.breaking;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockBreakCheck;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockBreak;
import ac.grim.grimac.utils.inventory.Inventory;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

@CheckData(name = "AutoTool", description = "Suspicious tool swaps", experimental = true)
public class AutoTool extends Check implements PacketCheck, BlockBreakCheck {

    private static final int MIN_SWITCH_DELAY = 150; // ms
    private static final int BACK_SWITCH_DELAY = 150; // ms
    private static final int MAX_PING = 400; // ms

    private static final class Swap {
        final long time;
        final int fromSlot, toSlot;
        final ItemStack fromItem, toItem;
        Swap(long t, int fS, int tS, ItemStack fI, ItemStack tI) {
            time = t; fromSlot = fS; toSlot = tS; fromItem = fI; toItem = tI;
        }
    }

    private static final class Click {
        final long time;
        final WrappedBlockState block;
        final int slot;
        final ItemStack held;
        Click(long t, WrappedBlockState b, int s, ItemStack h) {
            time = t; block = b; slot = s; held = h;
        }
    }

    private Swap lastSwap;
    private Click lastClick;
    private long digStart;
    private int streak;
    private long streakStart;
    private long lastCorrectSwapTime;
    private int originalSlot;

    public AutoTool(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            handleSwap(event);
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() == DiggingAction.START_DIGGING) {
                handleDigStart(dig);
            }
        }
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == DiggingAction.START_DIGGING) {
            long now = System.currentTimeMillis();
            lastClick = new Click(now, blockBreak.block, player.packetStateData.lastSlotSelected,
                    player.getInventory().getHeldItem());
            digStart = now;
            evaluateBeforeHit(blockBreak.block);
        }
    }

    private void handleDigStart(WrapperPlayClientPlayerDigging dig) {
        // already handled in onBlockBreak via CheckManagerListener
    }

    private void handleSwap(PacketReceiveEvent event) {
        long now = System.currentTimeMillis();
        int newSlot = new WrapperPlayClientHeldItemChange(event).getSlot();
        int prevSlot = player.packetStateData.lastSlotSelected;
        ItemStack fromItem = player.getInventory().inventory.getInventoryStorage()
                .getItem(prevSlot + Inventory.HOTBAR_OFFSET);
        ItemStack toItem = player.getInventory().inventory.getInventoryStorage()
                .getItem(newSlot + Inventory.HOTBAR_OFFSET);

        if (lastCorrectSwapTime > 0 && now - lastCorrectSwapTime <= BACK_SWITCH_DELAY && newSlot == originalSlot) {
            flagAndAlert("back-switch");
            lastCorrectSwapTime = 0;
            originalSlot = -1;
        }

        lastSwap = new Swap(now, prevSlot, newSlot, fromItem, toItem);

        // swap after hit
        if (lastClick != null) {
            long delay = now - lastClick.time;
            if (delay <= MIN_SWITCH_DELAY && now - digStart < MIN_SWITCH_DELAY) {
                evaluateSuspicion(lastClick.held, toItem, delay, lastClick.block, lastClick.slot);
            }
        }
    }

    private void evaluateBeforeHit(WrappedBlockState block) {
        if (lastSwap == null) return;
        long delay = lastClick.time - lastSwap.time;
        if (delay < 0 || delay > MIN_SWITCH_DELAY) return;
        if (lastSwap.time - digStart >= MIN_SWITCH_DELAY) return;
        evaluateSuspicion(lastSwap.fromItem, lastSwap.toItem, delay, block, lastSwap.fromSlot);
    }

    private void evaluateSuspicion(ItemStack wrongRaw, ItemStack right, long delay, WrappedBlockState block, int origSlot) {
        if (right == null) return;
        ItemType rightType = right.getType();
        ItemType wrongType = wrongRaw == null ? null : wrongRaw.getType();

        String blockName = block.getType().getName();
        if (!isCorrectTool(blockName, rightType)) return;
        if (wrongType != null && wrongType != ItemTypes.AIR && isCorrectTool(blockName, wrongType)) return;
        if (player.getTransactionPing() > MAX_PING) return;

        int add = delay <= 80 ? 20 : 10;
        long now = System.currentTimeMillis();
        if (now - streakStart <= 5000) {
            streak++;
        } else {
            streak = 1;
            streakStart = now;
        }
        if (streak >= 4) add += 30;

        lastCorrectSwapTime = now;
        originalSlot = origSlot;

        if (flagAndAlert("delay=" + delay)) {
            // no packet modification
        }
    }

    private boolean isCorrectTool(String blockName, ItemType tool) {
        if (tool == null) return false;
        String t = tool.getName().getKey();
        if ("SHEARS".equals(t))
            return blockName.contains("LEAVES") || blockName.contains("WOOL") || blockName.equals("COBWEB");
        if (t.endsWith("_SWORD"))
            return blockName.equals("BAMBOO") || blockName.equals("BAMBOO_SHOOT");
        boolean axe = t.endsWith("_AXE");
        boolean pick = t.endsWith("_PICKAXE");
        boolean shovel = t.endsWith("_SHOVEL");
        boolean hoe = t.endsWith("_HOE");
        if (pick && (blockName.contains("STONE") || blockName.contains("DEEPSLATE") || blockName.contains("ORE") || blockName.contains("TERRACOTTA") || blockName.endsWith("_BLOCK") || blockName.equals("OBSIDIAN") || blockName.equals("CRYING_OBSIDIAN") || blockName.equals("NETHERRACK") || blockName.equals("END_STONE") || (blockName.startsWith("RAW_") && blockName.endsWith("_BLOCK")) || blockName.equals("ANCIENT_DEBRIS")))
            return true;
        if (axe && (blockName.contains("WOOD") || blockName.endsWith("_LOG") || blockName.contains("PLANKS") || blockName.contains("BAMBOO") || blockName.contains("CHEST") || blockName.equals("BARREL") || blockName.contains("BOOKSHELF") || blockName.equals("LADDER") || blockName.contains("SIGN") || blockName.contains("CAMPFIRE") || blockName.equals("NOTE_BLOCK") || blockName.endsWith("_TABLE")))
            return true;
        if (shovel && (blockName.contains("DIRT") || blockName.contains("GRAVEL") || blockName.contains("SAND") || blockName.contains("SNOW") || blockName.contains("MUD") || blockName.contains("CLAY") || blockName.equals("GRASS_BLOCK") || blockName.equals("PODZOL") || blockName.equals("ROOTED_DIRT") || blockName.endsWith("CONCRETE_POWDER") || blockName.equals("SOUL_SAND") || blockName.equals("SOUL_SOIL")))
            return true;
        if (hoe && (blockName.contains("HAY") || blockName.contains("CROP") || blockName.contains("WART") || blockName.contains("LEAVES") || blockName.contains("MOSS") || blockName.equals("DRIED_KELP_BLOCK") || blockName.equals("TARGET")))
            return true;
        return false;
    }
}