package bisq.network.p2p.services.confidential.ack;

import bisq.common.proto.ProtoEnum;
import bisq.common.proto.ProtobufUtils;
import bisq.i18n.Res;
import lombok.Getter;

@Getter
public enum MessageDeliveryStatus implements ProtoEnum {
    CONNECTING,
    SENT,
    ACK_RECEIVED(true),
    TRY_ADD_TO_MAILBOX,
    ADDED_TO_MAILBOX,
    MAILBOX_MSG_RECEIVED(true),
    FAILED;

    private final boolean received;

    MessageDeliveryStatus() {
        this(false);
    }

    MessageDeliveryStatus(boolean received) {
        this.received = received;
    }

    public boolean isPending() {
        return this == CONNECTING || this == SENT || this == TRY_ADD_TO_MAILBOX;
    }

    @Override
    public bisq.network.protobuf.MessageDeliveryStatus toProtoEnum() {
        return bisq.network.protobuf.MessageDeliveryStatus.valueOf(getProtobufEnumPrefix() + name());
    }

    public static MessageDeliveryStatus fromProto(bisq.network.protobuf.MessageDeliveryStatus proto) {
        return ProtobufUtils.enumFromProto(MessageDeliveryStatus.class, proto.name(), CONNECTING);
    }

    public String getDisplayString() {
        return switch (this) {
            case CONNECTING -> Res.get("muSig.tradeState.requestMediation.deliveryState.CONNECTING");
            case SENT -> Res.get("muSig.tradeState.requestMediation.deliveryState.SENT");
            case ACK_RECEIVED -> Res.get("muSig.tradeState.requestMediation.deliveryState.ACK_RECEIVED");
            case TRY_ADD_TO_MAILBOX -> Res.get("muSig.tradeState.requestMediation.deliveryState.TRY_ADD_TO_MAILBOX");
            case ADDED_TO_MAILBOX -> Res.get("muSig.tradeState.requestMediation.deliveryState.ADDED_TO_MAILBOX");
            case MAILBOX_MSG_RECEIVED ->
                    Res.get("muSig.tradeState.requestMediation.deliveryState.MAILBOX_MSG_RECEIVED");
            case FAILED -> Res.get("muSig.tradeState.requestMediation.deliveryState.FAILED");
        };
    }
}
