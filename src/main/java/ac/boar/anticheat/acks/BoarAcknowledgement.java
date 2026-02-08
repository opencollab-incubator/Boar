package ac.boar.anticheat.acks;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.player.BoarPlayer;
import lombok.Getter;
import org.cloudburstmc.netty.channel.raknet.packet.RakDatagramPacket;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;

import java.util.HashMap;
import java.util.Map;

public class BoarAcknowledgement {
    @Getter
    private static final Map<RakSessionCodec, BoarPlayer> rakSessionToPlayer = new HashMap<>();

    public static void handle(final RakSessionCodec codec, final RakDatagramPacket datagram) {
//        BoarPlayer player = rakSessionToPlayer.get(codec);
//        if (player == null) {
//            return;
//        }
//
//        if (player.isClosed()) {
//            return;
//        }
//
//        if (player.receivedStackId.get() == player.sentStackId.get()) {
//            return;
//        }
//
//        long lastLatency = player.getLatencyUtil().getLastSentTime().ms();
//
//        long distance = datagram.getSendTime() - lastLatency;
//        if (distance <= Boar.getConfig().maxAcknowledgementTime() || lastLatency == -1 || player.inLoadingScreen || player.sinceLoadingScreen < 5) {
//            return;
//        }
//
//        player.getLatencyUtil().confirmByTime(datagram.getSendTime());
    }
}
