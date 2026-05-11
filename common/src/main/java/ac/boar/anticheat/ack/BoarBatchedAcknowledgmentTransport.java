package ac.boar.anticheat.ack;

import java.util.List;

public interface BoarBatchedAcknowledgmentTransport extends BoarAcknowledgmentTransport {

    void markPending(Acknowledgment ack);

    List<Acknowledgment> drainPending();

    boolean hasPending();
}
