package chipsat;

public class MissionDecision {
    public enum Status {
        DELIVERED,
        STORED,
        DROPPED
    }

    private final Status status;
    private final TelemetryPacket packet;
    private final String message;

    private MissionDecision(Status status, TelemetryPacket packet, String message) {
        this.status = status;
        this.packet = packet;
        this.message = message;
    }

    public static MissionDecision delivered(TelemetryPacket packet, String message) {
        return new MissionDecision(Status.DELIVERED, packet, message);
    }

    public static MissionDecision stored(TelemetryPacket packet, String message) {
        return new MissionDecision(Status.STORED, packet, message);
    }

    public static MissionDecision dropped(TelemetryPacket packet, String message) {
        return new MissionDecision(Status.DROPPED, packet, message);
    }

    public Status getStatus() {
        return status;
    }

    public TelemetryPacket getPacket() {
        return packet;
    }

    public String getMessage() {
        return message;
    }
}
