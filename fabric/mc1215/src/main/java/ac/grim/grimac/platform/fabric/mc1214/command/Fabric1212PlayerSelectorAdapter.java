package ac.grim.grimac.platform.fabric.mc1214.command;


import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.platform.sender.Sender;
import ac.grim.grimac.platform.fabric.mc1161.command.Fabric1161PlayerSelectorAdapter;
import ac.grim.grimac.platform.fabric.sender.FabricSenderFactory;


public class Fabric1212PlayerSelectorAdapter extends Fabric1161PlayerSelectorAdapter {

    public Fabric1212PlayerSelectorAdapter(org.incendo.cloud.minecraft.modded.data.SinglePlayerSelector fabricSelector) {
        super(fabricSelector);
    }

    // 1.21.2 .getCommandSource() moves from entity to player
    @Override
    public Sender getSinglePlayer() {
        return ((FabricSenderFactory) GrimAPIProvider.getDirect().getPlatformLoader().getSenderFactory()).map(fabricSelector.single().getCommandSource());
    }
}
