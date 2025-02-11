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
    public void reload(ConfigManager config) {
        List<String> punish = config.getStringListElse("Punishments", new ArrayList<>());
        experimentalSymbol = config.getStringElse("experimental-symbol", "*");
        alertString = config.getStringElse("alerts-format",
                "%prefix% &f%player% &bfailed &f%check_name% &f(x&c%vl%&f) &7%verbose%");
        testMode = config.getBooleanElse("test-mode", false);
        printToConsole = config.getBooleanElse("verbose.print-to-console", false);
        proxyAlertString = config.getStringElse("alerts-format-proxy",
                "%prefix% &f[&cproxy&f] &f%player% &bfailed &f%check_name% &f(x&c%vl%&f) &7%verbose%");

        try {
            groups.clear();

            // To support reloading
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

    private String replaceAlertPlaceholders(String original, int vl, PunishGroup group,
                                            Check check, String alertString, String verbose) {
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
     * Called by checks (e.g. OffsetHandler) that have flagged the player.
     * This variant allows you to pass a partial increment (scaled VL).
     */
    public void handleViolation(Check check, double partialIncrement) {
        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                long currentTime = System.currentTimeMillis();

                // Store partial increments under a timestamp
                group.violations.put(currentTime, new ViolationIncrement(check, partialIncrement));

                // Remove expired entries
                group.violations.entrySet().removeIf(entry ->
                        currentTime - entry.getKey() > group.removeViolationsAfter
                );
            }
        }
    }

    /**
     * Fallback method for checks that do not do partial increments.
     * (We can call this with an increment of 1.0 under the hood.)
     */
    public void handleViolation(Check check) {
        handleViolation(check, 1.0);
    }

    public boolean handleAlert(GrimPlayer player, String verbose, Check check) {
        boolean sentDebug = false;

        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                // Sum partial increments for this check => "vl"
                final int vl = (int) Math.ceil(getViolationsSum(group, check));

                // For old code that used 'violationCount = group.violations.size()', just remove it;
                // we rely on the partial-increment sum "vl" for thresholds now.
                for (ParsedCommand command : group.commands) {
                    String cmd = replaceAlertPlaceholders(vl, group, check, alertString, verbose);

                    // Verbose prints
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

                    // Check threshold
                    if (vl >= command.threshold) {
                        // 0 => run once, otherwise run every X increments
                        boolean inInterval = command.interval == 0
                                ? (command.executeCount == 0)
                                : (vl % command.interval == 0);

                        if (inInterval) {
                            CommandExecuteEvent executeEvent = new CommandExecuteEvent(player, check, verbose, cmd);
                            Bukkit.getPluginManager().callEvent(executeEvent);
                            if (executeEvent.isCancelled()) continue;

                            if (command.command.equals("[webhook]")) {
                                GrimAPI.INSTANCE.getDiscordManager()
                                        .sendAlert(player, verbose, check.getDisplayName(), vl);
                            } else if (command.command.equals("[log]")) {
                                // For logging, sum how many increments total for this check.
                                int vls = (int) Math.ceil(getViolationsSum(group, check));
                                String verboseWithoutGl = verbose.replaceAll(" /gl .*", "");
                                GrimAPI.INSTANCE.getViolationDatabaseManager()
                                        .logAlert(player, verboseWithoutGl, check.getDisplayName(), vls);
                            } else if (command.command.equals("[proxy]")) {
                                ProxyAlertMessenger.sendPluginMessage(
                                        replaceAlertPlaceholders(vl, group, check, proxyAlertString, verbose));
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
                                FoliaScheduler.getGlobalRegionScheduler().run(
                                        GrimAPI.INSTANCE.getPlugin(),
                                        (dummy) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd)
                                );
                            }
                        }
                        command.executeCount++;
                    }
                }
            }
        }
        return sentDebug;
    }

    /**
     * Sum all unexpired increments for this check in the group.
     */
    private double getViolationsSum(PunishGroup group, Check check) {
        double sum = 0;
        for (ViolationIncrement record : group.violations.values()) {
            if (record.check == check) {
                sum += record.partialIncrement;
            }
        }
        return sum;
    }

    /**
     * In case some checks still call this (non-partial) method,
     * it just does handleViolation(check, 1.0).
     */
    @Deprecated
    public void handleViolation(Check check) {
        handleViolation(check, 1.0);
    }

    /**
     * Quick placeholder to preserve the old signature used in replaceAlertPlaceholders.
     */
    private String replaceAlertPlaceholders(int vl, PunishGroup group,
                                            Check check, String alertString, String verbose) {
        String original = alertString
                .replace("[alert]", alertString)
                .replace("[proxy]", alertString)
                .replace("%check_name%", check.getDisplayName())
                .replace("%experimental%", check.isExperimental() ? experimentalSymbol : "")
                .replace("%vl%", Integer.toString(vl))
                .replace("%verbose%", verbose)
                .replace("%description%", check.getDescription());
        return MessageUtil.replacePlaceholders(player, original);
    }

    @Override
    public void reload(ConfigManager config) {
        // Implementation unchanged, see above ...
    }

    // ...
}

class PunishGroup {
    public final List<AbstractCheck> checks;
    public final List<ParsedCommand> commands;
    // We store (timestamp -> partial increment) in this map:
    public final Map<Long, ViolationIncrement> violations = new HashMap<>();
    public final int removeViolationsAfter;

    public PunishGroup(List<AbstractCheck> checks, List<ParsedCommand> commands, int removeViolationsAfter) {
        this.checks = checks;
        this.commands = commands;
        // Convert from seconds to milliseconds
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
 * Simple container for storing a partial increment in the map
 * along with which check caused it.
 */
class ViolationIncrement {
    public final Check check;
    public final double partialIncrement;

    public ViolationIncrement(Check check, double partialIncrement) {
        this.check = check;
        this.partialIncrement = partialIncrement;
    }
}
