package ac.grim.grimac.manager.init.start;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.commands.*;
import ac.grim.grimac.manager.init.Initable;
import co.aikar.commands.PaperCommandManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.player.GrimPlayer;

import java.util.Set;
import java.util.TreeSet;

public class CommandRegister implements Initable {
    @Override
    public void start() {
        // This does not make Grim require paper
        // It only enables new features such as asynchronous tab completion on paper
        PaperCommandManager commandManager = new PaperCommandManager(GrimAPI.INSTANCE.getPlugin());

        commandManager.registerCommand(new GrimPerf());
        commandManager.registerCommand(new GrimDebug());
        commandManager.registerCommand(new GrimAlerts());
        commandManager.registerCommand(new GrimProfile());
        commandManager.registerCommand(new GrimSendAlert());
        commandManager.registerCommand(new GrimHelp());
        commandManager.registerCommand(new GrimReload());
        commandManager.registerCommand(new GrimSpectate());
        commandManager.registerCommand(new GrimStopSpectating());
        commandManager.registerCommand(new GrimLog());
        commandManager.registerCommand(new GrimVerbose());
        commandManager.registerCommand(new GrimVersion());
        commandManager.registerCommand(new GrimDump());
        commandManager.registerCommand(new GrimBrands());
        commandManager.registerCommand(new GrimResetVl());

        commandManager.getCommandCompletions().registerCompletion("stopspectating", GrimStopSpectating.completionHandler);

        commandManager.getCommandCompletions().registerCompletion("checks", context -> {
            Set<String> suggestions = new TreeSet<>();
            suggestions.add("all");
            if (context.getSender() instanceof Player) {
                Player player = (Player) context.getSender();
                GrimPlayer gp = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(player);
                if (gp != null) {
                    for (AbstractCheck absCheck : gp.checkManager.allChecks.values()) {
                        if (absCheck instanceof Check) {
                            Check c = (Check) absCheck;
                            if (c.getCheckName() != null && !c.getCheckName().isEmpty()) {
                                suggestions.add(c.getCheckName());
                            }
                            if (c.getAlternativeName() != null && !c.getAlternativeName().isEmpty()) {
                                suggestions.add(c.getAlternativeName());
                            }
                        }
                    }
                }
            } else {
                for (GrimPlayer gp : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                    for (AbstractCheck absCheck : gp.checkManager.allChecks.values()) {
                        if (absCheck instanceof Check) {
                            Check c = (Check) absCheck;
                            if (c.getCheckName() != null && !c.getCheckName().isEmpty()) {
                                suggestions.add(c.getCheckName());
                            }
                            if (c.getAlternativeName() != null && !c.getAlternativeName().isEmpty()) {
                                suggestions.add(c.getAlternativeName());
                            }
                        }
                    }
                }
            }
            return suggestions;
        });

        if (GrimAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("check-for-updates", true)) {
            GrimVersion.checkForUpdatesAsync(Bukkit.getConsoleSender());
        }
    }
}
