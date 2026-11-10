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

import java.util.List;

@RequiredArgsConstructor
public class BoarHandlerAdaptor extends MessageToMessageCodec<BedrockPacketWrapper, BedrockPacketWrapper> implements PacketInjector {
    private final BoarPlayer player;
    private final BedrockPacketCodec codec;

    public static final String NAME = "boar-packet-handler";

    private ChannelHandlerContext ctx;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        this.ctx = null;
    }

    /**
     * Push a synthetic Bedrock packet downstream as if the client had just sent it.
     *
     * The packet is fired from this handler's context, so it skips the {@link PacketListener}
     * chain on its way to the next handler. Callers that buffer a packet for replay must
     * retain any reference-counted payload before caching — the wrapper built here releases
     * the packet when its own refcount drops to zero.
     */
    @Override
    public void injectClientPacket(BedrockPacket packet) {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null || player.isClosed()) {
            return;
        }

        Runnable task = () -> {
            if (player.isClosed()) {
                return;
            }
            BedrockPacketWrapper wrapper = BedrockPacketWrapper.create(
                    this.codec.getPacketId(packet), 0, 0, packet, null);
            ctx.fireChannelRead(wrapper);
        };

        if (ctx.executor().inEventLoop()) {
            task.run();
        } else {
            ctx.executor().execute(task);
        }
    }

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
                    Boar.getInstance().getPlatform().logger().error(this.player.getSession().name() + ": onPacketSend listener " + listener.getClass().getSimpleName() + " threw for packet " + event.getPacket().getClass().getSimpleName(), t);
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(this.player.getSession().name() + ": onPacketSend listener loop threw for packet " + event.getPacket().getClass().getSimpleName(), e);
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
        } catch (Exception e) {
            // The packet is dropped when encoding fails.
            Boar.getInstance().getPlatform().logger().error(this.player.getSession().name() + ": failed to encode packet " + event.getPacket().getClass().getSimpleName(), e);
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
                    Boar.getInstance().getPlatform().logger().error(this.player.getSession().name() + ": onPacketReceived listener " + listener.getClass().getSimpleName() + " threw for packet " + event.getPacket().getClass().getSimpleName(), t);
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(this.player.getSession().name() + ": onPacketReceived listener loop threw for packet " + event.getPacket().getClass().getSimpleName(), e);
        }

        if (event.isCancelled()) {
            return;
        }

        msg.setPacket(event.getPacket());
        out.add(msg.retain());

        event.getPostTasks().forEach(Runnable::run);
    }
}
