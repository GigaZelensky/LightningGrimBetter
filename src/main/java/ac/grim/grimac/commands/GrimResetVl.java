package ac.grim.grimac.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.player.GrimPlayer;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.annotation.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@CommandAlias("grim|grimac")
public class GrimResetVl extends BaseCommand {

    @Subcommand("resetvl")
    @CommandPermission("grim.resetvl")
    @Syntax("<player|@a> <check|all>")
    @CommandCompletion("@players checks")
    public void onResetVl(CommandSender sender, String target, String checkArg) {
        List<GrimPlayer> targetPlayers = new ArrayList<>();
        if (target.equalsIgnoreCase("@a")) {
            targetPlayers.addAll(GrimAPI.INSTANCE.getPlayerDataManager().getEntries());
        } else {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(target);
            for (GrimPlayer gp : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                if (gp.uuid.equals(offlinePlayer.getUniqueId())) {
                    targetPlayers.add(gp);
                    break;
                }
            }
        }
        if (targetPlayers.isEmpty()) {
            sender.sendMessage("No players found for target: " + target);
            return;
        }
        
        boolean resetAll = checkArg.equalsIgnoreCase("all");
        for (GrimPlayer gp : targetPlayers) {
            if (resetAll) {
                gp.punishmentManager.resetAllViolations();
                sender.sendMessage("Reset all violations for player " + (gp.bukkitPlayer != null ? gp.bukkitPlayer.getName() : gp.uuid));
            } else {
                Check foundCheck = null;
                // Search for a matching check from the player's checks (match by check name or alternative name)
                for (AbstractCheck absCheck : gp.checkManager.allChecks.values()) {
                    if (absCheck instanceof Check) {
                        Check c = (Check) absCheck;
                        if ((c.getCheckName() != null && c.getCheckName().equalsIgnoreCase(checkArg))
                                || (c.getAlternativeName() != null && c.getAlternativeName().equalsIgnoreCase(checkArg))) {
                            foundCheck = c;
                            break;
                        }
                    }
                }
                if (foundCheck == null) {
                    sender.sendMessage("No check found named \"" + checkArg + "\" for player " 
                            + (gp.bukkitPlayer != null ? gp.bukkitPlayer.getName() : gp.uuid));
                } else {
                    gp.punishmentManager.resetViolationsForCheck(foundCheck);
                    sender.sendMessage("Reset violations for check \"" + foundCheck.getCheckName() + "\" for player " 
                            + (gp.bukkitPlayer != null ? gp.bukkitPlayer.getName() : gp.uuid));
                }
            }
        }
    }
}
