package ac.grim.grimac.platform.bukkit.manager;

import ac.grim.grimac.api.platform.command.PlayerSelector;
import ac.grim.grimac.api.platform.manager.ParserDescriptorFactory;
import ac.grim.grimac.api.platform.sender.Sender;
import ac.grim.grimac.platform.bukkit.command.BukkitPlayerSelectorParser;
import org.incendo.cloud.parser.ParserDescriptor;

public class BukkitParserDescriptorFactory implements ParserDescriptorFactory {

    BukkitPlayerSelectorParser<Sender> bukkitPlayerSelectorParser = new BukkitPlayerSelectorParser<>();

    @Override
    public ParserDescriptor<Sender, PlayerSelector> getSinglePlayer() {
        return bukkitPlayerSelectorParser.descriptor();
    }
}
