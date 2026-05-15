package ac.boar.protocol;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.List;

@RequiredArgsConstructor
public class BoarHandlerAdaptor extends MessageToMessageCodec<BedrockPacketWrapper, BedrockPacketWrapper> {
    private final BoarPlayer player;
    private final BedrockPacketCodec codec;

    public static final String NAME = "boar-packet-handler";

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockPacketWrapper msg, List<Object> out) {
        if (player.isClosed()) {
            return;
        }

        final CloudburstPacketEvent event = new CloudburstPacketEvent(this.player, msg.getPacket());
        try {
            for (final PacketListener listener : PacketEvents.getApi().getListeners()) {
                try {
                    listener.onPacketSend(event);
                } catch (Throwable t) {
                    Boar.debug("[listener-fail] onPacketSend listener=" + listener.getClass().getSimpleName() + " packet=" + event.getPacket().getClass().getSimpleName() + " err=" + t + "\n" + stackTrace(t), Boar.DebugMessage.SERVE);
                }
            }
        } catch (Exception ignored) {
        }

        if (event.isCancelled()) {
            return;
        }

        msg.setPacketBuffer(null);

        ByteBuf buf = ctx.alloc().buffer(128);
        try {
            BedrockPacket packet = event.getPacket();
            msg.setPacketId(this.codec.getPacketId(packet));
            this.codec.encodeHeader(buf, msg);
            this.codec.getCodec().tryEncode(this.codec.getHelper(), buf, packet);

            msg.setPacketBuffer(buf.retain());
            out.add(msg.retain());
        } catch (Exception ignored) {
        } finally {
            buf.release();
        }

        event.getPostTasks().forEach(Runnable::run);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockPacketWrapper msg, List<Object> out) {
        if (player.isClosed()) {
            return;
        }

        final CloudburstPacketEvent event = new CloudburstPacketEvent(this.player, msg.getPacket());
        try {
            for (final PacketListener listener : PacketEvents.getApi().getListeners()) {
                try {
                    listener.onPacketReceived(event);
                } catch (Throwable t) {
                    Boar.debug("[listener-fail] onPacketReceived listener=" + listener.getClass().getSimpleName() + " packet=" + event.getPacket().getClass().getSimpleName() + " err=" + t + "\n" + stackTrace(t), Boar.DebugMessage.SERVE);
                }
            }
        } catch (Exception ignored) {
        }

        if (event.isCancelled()) {
            return;
        }

        msg.setPacket(event.getPacket());
        out.add(msg.retain());

        event.getPostTasks().forEach(Runnable::run);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
