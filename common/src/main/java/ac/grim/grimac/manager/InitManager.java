package ac.grim.grimac.manager;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.platform.init.Initable;
import ac.grim.grimac.api.platform.init.LoadableInitable;
import ac.grim.grimac.api.platform.init.StoppableInitable;
import ac.grim.grimac.manager.init.load.PacketEventsInit;
import ac.grim.grimac.manager.init.start.*;
import ac.grim.grimac.api.platform.init.StartableInitable;
import ac.grim.grimac.manager.init.stop.TerminatePacketEvents;
import ac.grim.grimac.api.platform.sender.Sender;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.incendo.cloud.CommandManager;

import java.util.ArrayList;
import java.util.function.Supplier;

public class InitManager {

    private final ImmutableList<LoadableInitable> initializersOnLoad;
    private final ImmutableList<StartableInitable> initializersOnStart;
    private final ImmutableList<StoppableInitable> initializersOnStop;

    @Getter
    private boolean loaded = false;
    @Getter
    private boolean started = false;
    @Getter
    private boolean stopped = false;

    public InitManager(GrimAPI api, PacketEventsAPI<?> packetEventsAPI, Supplier<CommandManager<Sender>> commandManager, Initable... platformSpecificInitables) {
        ArrayList<LoadableInitable> extraLoadableInitables = new ArrayList<>();
        ArrayList<StartableInitable> extraStartableInitables = new ArrayList<>();
        ArrayList<StoppableInitable> extraStoppableInitables = new ArrayList<>();
        for (Initable initable : platformSpecificInitables) {
            if (initable instanceof LoadableInitable) extraLoadableInitables.add((LoadableInitable) initable);
            if (initable instanceof StartableInitable) extraStartableInitables.add((StartableInitable) initable);
            if (initable instanceof StoppableInitable) extraStoppableInitables.add((StoppableInitable) initable);
        }

        initializersOnLoad = ImmutableList.<LoadableInitable>builder()
                .add(() -> api.getExternalAPI().load())
                .add(new PacketEventsInit(packetEventsAPI))
                .add(new CommandRegister(commandManager))
                .addAll(extraLoadableInitables)
                .build();

        initializersOnStart = ImmutableList.<StartableInitable>builder()
                .add(api.getExternalAPI())
                .add(new ExemptOnlinePlayersOnReload())
                .add(new PacketManager(api))
                .add(new ViaBackwardsManager())
                .add(new TickRunner())
                .add(new PacketLimiter())
                .add(api.getAlertManager())
                .add(api.getDiscordManager())
                .add(api.getSpectateManager())
                .add(new JavaVersion())
                .add(new ViaVersion())
                .add(new TAB())
                .addAll(extraStartableInitables)
                .build();

        initializersOnStop = ImmutableList.<StoppableInitable>builder()
                .add(new TerminatePacketEvents())
                .addAll(extraStoppableInitables)
                .build();
    }

    public void load() {
        for (LoadableInitable initable : initializersOnLoad)
            try {
                initable.load();
            } catch (Exception e) {
                e.printStackTrace();
            }
        loaded = true;
    }

    public void start() {
        for (StartableInitable initable : initializersOnStart)
            try {
                initable.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        started = true;
    }

    public void stop() {
        for (StoppableInitable initable : initializersOnStop)
            try {
                initable.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        stopped = true;
    }
}
