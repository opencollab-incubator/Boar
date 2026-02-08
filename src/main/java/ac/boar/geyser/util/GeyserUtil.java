package ac.boar.geyser.util;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.BoarHandlerAdaptor;
import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;

import java.lang.reflect.Field;

public class GeyserUtil {
    public final static long MAGIC_FORM_IMAGE_HACK_TIMESTAMP = -1234567890L;
    public static final long MAGIC_VIRTUAL_INVENTORY_HACK = -9876543210L;

    public static void hook(final BoarPlayer player) {
        try {
            BedrockServerSession session = findCloudburstSession(player.getSession());
            player.setBedrockSession(session);
            final Channel channel = session.getPeer().getChannel();
            channel.pipeline().addAfter(BedrockPacketCodec.NAME, BoarHandlerAdaptor.NAME, new BoarHandlerAdaptor(player, (BedrockPacketCodec) channel.pipeline().get(BedrockPacketCodec.NAME)));
        } catch (Exception ignored) {
            player.kick("Failed to hook into bedrock channel pipeline!");
        }
    }

    private static BedrockServerSession findCloudburstSession(final GeyserSession connection) throws Exception {
        final Field upstream = GeyserSession.class.getDeclaredField("upstream");
        upstream.setAccessible(true);
        final Object session = upstream.get(connection);
        final Field field = UpstreamSession.class.getDeclaredField("session");
        field.setAccessible(true);
        return (BedrockServerSession) field.get(session);
    }
}