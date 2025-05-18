package ac.grim.grimac.utils.data;

import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.api.packet.player.enums.InteractionHand;
import lombok.Getter;

// This is to keep all the packet data out of the main player class
// Helps clean up the player class and makes devs aware they are sync'd to the netty thread
public class PacketStateData {
    public boolean packetPlayerOnGround = false;
    public boolean lastPacketWasTeleport = false;
    public boolean cancelDuplicatePacket, lastPacketWasOnePointSeventeenDuplicate = false;
    public boolean lastTransactionPacketWasValid = false;
    public int lastSlotSelected;
    public InteractionHand eatingHand = InteractionHand.MAIN_HAND;
    public long lastRiptide = 0;
    public boolean tryingToRiptide = false;
    public int slowedByUsingItemTransaction = Integer.MIN_VALUE;
    public boolean receivedSteerVehicle = false;
    // This works on 1.8 only
    public boolean didLastLastMovementIncludePosition = false;
    public boolean didLastMovementIncludePosition = false;
    // This works on 1.21.2+ only
    public boolean didSendMovementBeforeTickEnd = false;
    public KnownInput knownInput = new KnownInput(false, false, false, false, false, false, false);
    public ImmutableVector3d lastClaimedPosition = MCPacket.getAPI().getVectorFactory().getImmutableVec3d(0, 0, 0);
    public float lastHealth, lastSaturation;
    public int lastFood;
    public boolean lastServerTransWasValid = false;
    @Getter
    private boolean slowedByUsingItem;
    @Getter
    private int slowedByUsingItemSlot = Integer.MIN_VALUE;

    // If true, the player's rotation was forced to the horse's rotation only on 1.13-
    public boolean horseInteractCausedForcedRotation = false;

    public void setSlowedByUsingItem(boolean slowedByUsingItem) {
        this.slowedByUsingItem = slowedByUsingItem;
        slowedByUsingItemSlot = slowedByUsingItem ? lastSlotSelected : Integer.MIN_VALUE;
    }
}
