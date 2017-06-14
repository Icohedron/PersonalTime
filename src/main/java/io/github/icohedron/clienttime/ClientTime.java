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

@Plugin(id = "client-time", name = "Client Time", version = "1.0-MC1.10.2", dependencies = @Dependency(id = "packetgate", version = "0.1.1"))
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
            logger.info("Client Time has successfully initialized");
        } else {
            logger.error("PacketGate is not installed. Client Time depends on PacketGate in order to work");
        }
    }

    private void initializeCommands() {
        Sponge.getServiceManager().provide(PermissionService.class).ifPresent(permissionService ->
            permissionService.newDescriptionBuilder(this).ifPresent(builder ->
                builder.id("clienttime.command")
                        .description(Text.of("A user with this command will be able to set their own client time. Does not affect server time."))
                        .assign(PermissionDescription.ROLE_USER, true)
                        .register())
        );

        CommandSpec clientTimeCommand = CommandSpec.builder()
                .description(Text.of("Set client time"))
                .permission("clienttime.command")
                .arguments(GenericArguments.onlyOne(GenericArguments.string(Text.of("time"))))
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("This command may only be executed by a player"));
                        return CommandResult.empty();
                    }
                    Player player = (Player)src;
                    String time = args.<String>getOne("time").get();
                    if (time.equalsIgnoreCase("day")) {
                        setClientTime(player, 1000);
                        return CommandResult.success();
                    } else if (time.equalsIgnoreCase("night")) {
                        setClientTime(player, 13000);
                        return CommandResult.success();
                    } else if (time.equalsIgnoreCase("reset")) {
                        resetClientTime(player);
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
                })
                .build();

        Sponge.getCommandManager().register(this, clientTimeCommand, "clienttime", "ctime");
    }

    private void sendMessage(Player player, String text) {
        player.sendMessage(Text.of(TextColors.GREEN, "[", TextColors.RED, "Client Time", TextColors.GREEN, "] ", TextColors.YELLOW, text));
    }

    private void setClientTime(Player player, long ticks) {
        World world = player.getWorld();
        long worldTime = world.getProperties().getWorldTime();
        long desiredClientTime = (long) Math.ceil(worldTime / 24000.0f) * 24000 + ticks;
        long timeOffset = desiredClientTime - worldTime;
        timeOffsets.put(player.getUniqueId(), timeOffset);

        sendMessage(player, "Set time to " + ticks);
    }

    private void resetClientTime(Player player) {
        timeOffsets.put(player.getUniqueId(), 0L);
        sendMessage(player, "Your time is now reset");
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
            clientWorldTime = -((-worldTime + timeOffsets.get(playerUuid)) % 24000); // gamerule doDaylightCycle is false, which makes worldTime negative
        } else {
            clientWorldTime = (worldTime + timeOffsets.get(playerUuid)) % 24000;
        }

        PacketBuffer newPacketBuffer = new PacketBuffer((Unpooled.buffer(16)));
        newPacketBuffer.writeLong(totalWorldTime);
        newPacketBuffer.writeLong(clientWorldTime);

        try {
            packet.readPacketData(newPacketBuffer);
        } catch (IOException e) {
            logger.error("Failed to write packet buffer");
        }
    }
}
