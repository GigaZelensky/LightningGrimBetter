package ac.grim.grimac.manager;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.config.ConfigReloadable;
import ac.grim.grimac.api.events.CommandExecuteEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.events.packets.ProxyAlertMessenger;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class PunishmentManager implements ConfigReloadable {
    GrimPlayer player;
    List<PunishGroup> groups = new ArrayList<>();
    String experimentalSymbol = "*";
    private String alertString;
    private boolean testMode;
    private boolean printToConsole;
    private String proxyAlertString = "";

    public PunishmentManager(GrimPlayer player) {
        this.player = player;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reload(ConfigManager config) {
        List<String> punish = config.getStringListElse("Punishments", new ArrayList<>());
        experimentalSymbol = config.getStringElse("experimental-symbol", "*");
        alertString = config.getStringElse("alerts-format", "%prefix% &f%player% &bfailed &f%check_name% &f(x&c%vl%&f) &7%verbose%");
        testMode = config.getBooleanElse("test-mode", false);
        printToConsole = config.getBooleanElse("verbose.print-to-console", false);
        proxyAlertString = config.getStringElse("alerts-format-proxy", "%prefix% &f[&cproxy&f] &f%player% &bfailed &f%check_name% &f(x&c%vl%&f) &7%verbose%");

        try {
            groups.clear();

            // To support reloading, disable checks by default
            for (AbstractCheck check : player.checkManager.allChecks.values()) {
                check.setEnabled(false);
            }

            for (Object s : punish) {
                LinkedHashMap<String, Object> map = (LinkedHashMap<String, Object>) s;

                List<String> checks = (List<String>) map.getOrDefault("checks", new ArrayList<>());
                List<String> commands = (List<String>) map.getOrDefault("commands", new ArrayList<>());
                int removeViolationsAfter = (int) map.getOrDefault("remove-violations-after", 300);

                List<ParsedCommand> parsed = new ArrayList<>();
                List<AbstractCheck> checksList = new ArrayList<>();
                List<AbstractCheck> excluded = new ArrayList<>();

                for (String command : checks) {
                    command = command.toLowerCase(Locale.ROOT);
                    boolean exclude = false;
                    if (command.startsWith("!")) {
                        exclude = true;
                        command = command.substring(1);
                    }
                    for (AbstractCheck check : player.checkManager.allChecks.values()) {
                        if (check.getCheckName() != null &&
                                (check.getCheckName().toLowerCase(Locale.ROOT).contains(command)
                                 || check.getAlternativeName().toLowerCase(Locale.ROOT).contains(command))) {
                            if (exclude) {
                                excluded.add(check);
                            } else {
                                checksList.add(check);
                                check.setEnabled(true);
                            }
                        }
                    }
                    for (AbstractCheck check : excluded) {
                        checksList.remove(check);
                    }
                }

                for (String command : commands) {
                    String firstNum = command.substring(0, command.indexOf(":"));
                    String secondNum = command.substring(command.indexOf(":"), command.indexOf(" "));
                    int threshold = Integer.parseInt(firstNum);
                    int interval = Integer.parseInt(secondNum.substring(1));
                    String commandString = command.substring(command.indexOf(" ") + 1);
                    parsed.add(new ParsedCommand(threshold, interval, commandString));
                }

                groups.add(new PunishGroup(checksList, parsed, removeViolationsAfter));
            }
        } catch (Exception e) {
            LogUtil.error("Error while loading punishments.yml! This is likely your fault!");
            e.printStackTrace();
        }
    }

    /**
     * NEW Overload to handle partial increments.
     * We'll store the violation in the group map, along with its increment.
     */
    public void handleViolation(Check check, double increment) {
        for (PunishGroup group : groups) {
            // If this check belongs to the group, store the partial increment
            if (group.checks.contains(check)) {
                long now = System.currentTimeMillis();
                // Put an entry that references this check & how much increment to add
                group.violations.put(now, new ViolationEntry(check, increment));

                // Remove old entries
                group.violations.entrySet().removeIf(e -> now - e.getKey() > group.removeViolationsAfter);
            }
        }
    }

    /**
     * DEPRECATED: Keep it if other checks call it.
     * This one just calls the new handleViolation(check, 1.0).
     */
    public void handleViolation(Check check) {
        handleViolation(check, 1.0);
    }

    /**
     * Replaces placeholders with the violation level from getViolations(...) (time-pruned).
     */
    private String replaceAlertPlaceholders(String original, int vl, PunishGroup group, Check check, String alertString, String verbose) {
        original = original
                .replace("[alert]", alertString)
                .replace("[proxy]", alertString)
                .replace("%check_name%", check.getDisplayName())
                .replace("%experimental%", check.isExperimental() ? experimentalSymbol : "")
                .replace("%vl%", Integer.toString(vl))
                .replace("%verbose%", verbose)
                .replace("%description%", check.getDescription());

        return MessageUtil.replacePlaceholders(player, original);
    }

    /**
     * This is invoked to determine if a broadcast is made.
     */
    public boolean handleAlert(GrimPlayer player, String verbose, Check check) {
        boolean sentDebug = false;

        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                // Count time-pruned violations
                final int vl = getViolations(group, check);

                for (ParsedCommand command : group.commands) {
                    String cmd = replaceAlertPlaceholders(command.command, vl, group, check, alertString, verbose);

                    // Verbose [alert]
                    if (!GrimAPI.INSTANCE.getAlertManager().getEnabledVerbose().isEmpty()
                            && command.command.equals("[alert]")) {
                        sentDebug = true;
                        for (Player bukkitPlayer : GrimAPI.INSTANCE.getAlertManager().getEnabledVerbose()) {
                            MessageUtil.sendMessage(bukkitPlayer, MessageUtil.miniMessage(cmd));
                        }
                        if (printToConsole) {
                            LogUtil.console(MessageUtil.miniMessage(cmd));
                        }
                    }

                    // The "threshold" logic
                    // 0 means execute once
                    // Any other number means execute every X interval
                    for (; vl >= (command.threshold + (command.interval * command.executeCount)); command.executeCount++) {
                        if (command.interval == 0 && command.executeCount > 0) break;

                        CommandExecuteEvent executeEvent = new CommandExecuteEvent(player, check, verbose, cmd);
                        Bukkit.getPluginManager().callEvent(executeEvent);
                        if (executeEvent.isCancelled()) continue;

                        if (command.command.equals("[webhook]")) {
                            GrimAPI.INSTANCE.getDiscordManager().sendAlert(player, verbose, check.getDisplayName(), vl);
                        } else if (command.command.equals("[log]")) {
                            // Count how many increments are in the map for this check
                            int vls = getViolations(group, check);
                            String verboseWithoutGl = verbose.replaceAll(" /gl .*", "");
                            GrimAPI.INSTANCE.getViolationDatabaseManager().logAlert(player, verboseWithoutGl, check.getDisplayName(), vls);
                        } else if (command.command.equals("[proxy]")) {
                            ProxyAlertMessenger.sendPluginMessage(
                                    replaceAlertPlaceholders(command.command, vl, group, check, proxyAlertString, verbose));
                        } else {
                            if (command.command.equals("[alert]")) {
                                sentDebug = true;
                                if (testMode) {
                                    player.user.sendMessage(MessageUtil.miniMessage(cmd));
                                    continue;
                                }
                                cmd = "grim sendalert " + cmd;
                            }

                            String finalCmd = cmd;
                            FoliaScheduler.getGlobalRegionScheduler().run(GrimAPI.INSTANCE.getPlugin(),
                                    (dummy) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
                        }
                    }
                }
            }
        }
        return sentDebug;
    }

    /**
     * Now sums partial increments for the check in question.
     * Only includes entries that remain after removing older ones in handleViolation().
     */
    private int getViolations(PunishGroup group, Check check) {
        double total = 0.0;
        for (ViolationEntry ve : group.violations.values()) {
            if (ve.getCheck() == check) {
                total += ve.getIncrement();
            }
        }
        return (int) Math.ceil(total);
    }
}

class PunishGroup {
    public final List<AbstractCheck> checks;
    public final List<ParsedCommand> commands;
    // Instead of Map<Long, Check>, store <Long, ViolationEntry> so we can hold increments
    public final Map<Long, ViolationEntry> violations = new HashMap<>();
    public final int removeViolationsAfter;

    public PunishGroup(List<AbstractCheck> checks, List<ParsedCommand> commands, int removeViolationsAfter) {
        this.checks = checks;
        this.commands = commands;
        this.removeViolationsAfter = removeViolationsAfter * 1000;
    }
}

class ParsedCommand {
    public final int threshold;
    public final int interval;
    public final String command;
    public int executeCount;

    public ParsedCommand(int threshold, int interval, String command) {
        this.threshold = threshold;
        this.interval = interval;
        this.command = command;
    }
}

/**
 * Holds a reference to which check the violation belongs to and how big the "increment" is.
 */
class ViolationEntry {
    private final Check check;
    private final double increment;

    public ViolationEntry(Check check, double increment) {
        this.check = check;
        this.increment = increment;
    }

    public Check getCheck() {
        return check;
    }

    public double getIncrement() {
        return increment;
    }
}
