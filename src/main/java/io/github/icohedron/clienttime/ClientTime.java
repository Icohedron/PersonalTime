package io.github.icohedron.clienttime;

import com.google.inject.Inject;
import eu.crushedpixel.sponge.packetgate.api.event.PacketEvent;
import eu.crushedpixel.sponge.packetgate.api.listener.PacketListenerAdapter;
import eu.crushedpixel.sponge.packetgate.api.registry.PacketConnection;
import eu.crushedpixel.sponge.packetgate.api.registry.PacketGate;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Plugin(id = "client-time", name = "ClientTime", version = "1.0-PRERELEASE", dependencies = @Dependency(id = "packetgate"))
public class ClientTime extends PacketListenerAdapter {

    @Inject
    private Logger logger;

    private Map<UUID, Long> timeOffsets; // <Player UUID, time offset in ticks>

    @Listener
    public void onInitializationEvent(GameInitializationEvent event) {
        timeOffsets = new HashMap<>();
        Optional<PacketGate> packetGateOptional = Sponge.getServiceManager().provide(PacketGate.class);
        if (packetGateOptional.isPresent()) {
            PacketGate packetGate = packetGateOptional.get();
            packetGate.registerListener(this, ListenerPriority.DEFAULT, SPacketTimeUpdate.class);
            initializeCommands();
            logger.info("ClientTime has successfully initialized");
        } else {
            logger.error("PacketGate is not installed. ClientTime depends on PacketGate in order to work");
        }
    }

    private void initializeCommands() {
        CommandSpec clientTimeSetCommand = CommandSpec.builder()
                .description(Text.of("Set client time"))
                .permission("clienttime.command.set")
                .arguments(GenericArguments.onlyOne(GenericArguments.string(Text.of("time"))))
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("This command may only be executed by a player"));
                        return CommandResult.empty();
                    }
                    Player player = (Player)src;
                    timeOffsets.putIfAbsent(player.getUniqueId(), 0L);
                    Optional<String> optionalTime = args.getOne("time");
                    if (optionalTime.isPresent()) {
                        String time = optionalTime.get();
                        if (time.equalsIgnoreCase("day")) {
                            setClientTime(player, 1000);
                            return CommandResult.success();
                        } else if (time.equalsIgnoreCase("night")) {
                            setClientTime(player, 14000);
                            return CommandResult.success();
                        } else {
                            int intTime;
                            try {
                                intTime = Integer.parseInt(time);
                            } catch (NumberFormatException e) {
                                sendMessage(player, "\'" + time + "\' is not a valid number");
                                return CommandResult.empty();
                            }
                            if (intTime < 0) {
                                sendMessage(player, "The number you have entered (" + time + ") is too small, it must be at least 0");
                                return CommandResult.empty();
                            }
                            setClientTime(player, intTime);
                            return CommandResult.success();
                        }
                    }
                    return CommandResult.empty();
                })
                .build();

        CommandSpec clientTimeResetCommand = CommandSpec.builder()
                .description(Text.of("Reset client time"))
                .permission("clienttime.command.reset")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("This command may only be executed by a player"));
                        return CommandResult.empty();
                    }
                    Player player = (Player)src;
                    resetClientTime(player);
                    return CommandResult.success();
                })
                .build();

        CommandSpec clientTimeStatusCommand = CommandSpec.builder()
                .description(Text.of("Get the status of the client time"))
                .permission("clienttime.command.status")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("This command may only be executed by a player"));
                        return CommandResult.empty();
                    }
                    Player player = (Player)src;
                    timeOffsets.putIfAbsent(player.getUniqueId(), 0L);
                    long ticksAhead = timeOffsets.get(player.getUniqueId());
                    if (ticksAhead == 0) {
                        sendMessage(player, "Your time is currently in sync with the server's");
                    }  else {
                        sendMessage(player, "Your time is currently running " + ticksAhead + " ticks ahead of the server's");
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec clientTimeCommand = CommandSpec.builder()
                .description(Text.of("The one command for ClientTime"))
                .permission("clienttime.command")
                .child(clientTimeSetCommand, "set")
                .child(clientTimeResetCommand, "reset")
                .child(clientTimeStatusCommand, "status")
                .build();

        Sponge.getCommandManager().register(this, clientTimeCommand, "clienttime", "ctime", "ptime");
    }

    private void sendMessage(Player player, String text) {
        player.sendMessage(Text.of(TextColors.GREEN, "[", TextColors.RED, "ClientTime", TextColors.GREEN, "] ", TextColors.YELLOW, text));
    }

    private String ticksToRealTime(long ticks) {
        int hours = (int) (ticks / 1000.0) + 6;
        int minutes = (int) (((ticks % 1000) / 1000.0) * 60.0);

        String suffix = "AM";

        if (hours >= 12) {
            hours -= 12;
            suffix = "PM";
            if (hours >= 12) {
                hours -= 12;
                suffix = "AM";
            }
        }

        if (hours == 0) {
            hours += 12;
        }

        return hours + ":" + String.format("%02d", minutes) + " " + suffix;
    }

    private void setClientTime(Player player, long ticks) {
        World world = player.getWorld();
        long worldTime = world.getProperties().getWorldTime();
        long desiredClientTime = (long) Math.ceil(worldTime / 24000.0f) * 24000 + ticks; // Fast forward to the next '0' time and add the desired number of ticks
        long timeOffset = desiredClientTime - worldTime;
        timeOffsets.put(player.getUniqueId(), timeOffset);

        sendMessage(player, "Set time to " + ticks + " (" + ticksToRealTime(ticks % 24000) + ")");
    }

    private void resetClientTime(Player player) {
        timeOffsets.put(player.getUniqueId(), 0L);
        sendMessage(player, "Your time is now synchronized with the server's");
    }

    @Override
    public void onPacketWrite(PacketEvent packetEvent, PacketConnection connection) {
        if (!(packetEvent.getPacket() instanceof SPacketTimeUpdate)) {
            return;
        }

        UUID playerUuid = connection.getPlayerUUID();
        timeOffsets.putIfAbsent(playerUuid, 0L);

        SPacketTimeUpdate packet = (SPacketTimeUpdate) packetEvent.getPacket();
        PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer(16));
        try {
            packet.writePacketData(packetBuffer);
        } catch (IOException e) {
            logger.error("Failed to read packet buffer");
            return;
        }

        long totalWorldTime = packetBuffer.readLong();
        long worldTime = packetBuffer.readLong();

        long clientWorldTime;
        if (worldTime < 0) {
            clientWorldTime = worldTime - timeOffsets.get(playerUuid); // gamerule doDaylightCycle is false, which makes worldTime negative
        } else {
            clientWorldTime = worldTime + timeOffsets.get(playerUuid);
        }

        packetEvent.setPacket(new SPacketTimeUpdate(totalWorldTime, clientWorldTime, true));
    }
}
