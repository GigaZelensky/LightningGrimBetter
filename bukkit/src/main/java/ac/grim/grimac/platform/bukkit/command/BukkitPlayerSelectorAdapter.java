package ac.grim.grimac.platform.bukkit.command;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.platform.command.PlayerSelector;
import ac.grim.grimac.api.platform.sender.Sender;
import ac.grim.grimac.platform.bukkit.sender.BukkitSenderFactory;

import java.util.Collection;
import java.util.Collections;

public class BukkitPlayerSelectorAdapter implements PlayerSelector {
    private final org.incendo.cloud.bukkit.data.SinglePlayerSelector bukkitSelector;

    public BukkitPlayerSelectorAdapter(org.incendo.cloud.bukkit.data.SinglePlayerSelector bukkitSelector) {
        this.bukkitSelector = bukkitSelector;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Sender getSinglePlayer() {
        return ((BukkitSenderFactory) GrimAPIProvider.getDirect().getPlatformLoader().getSenderFactory()).map(bukkitSelector.single());
    }

    @Override
    public Collection<Sender> getPlayers() {
        return Collections.singletonList(((BukkitSenderFactory) GrimAPIProvider.getDirect().getPlatformLoader().getSenderFactory()).map(bukkitSelector.single()));
    }

    @Override
    public String inputString() {
        return bukkitSelector.inputString();
    }
}
