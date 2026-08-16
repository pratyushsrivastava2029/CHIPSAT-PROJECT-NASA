package chipsat;

public class ScheduledDelivery {
    private final TelemetryPacket packet;
    private final ContactRoute route;
    private final int scheduledAtStep;

    public ScheduledDelivery(TelemetryPacket packet,
                             ContactRoute route,
                             int scheduledAtStep) {
        this.packet = packet;
        this.route = route;
        this.scheduledAtStep = scheduledAtStep;
    }

    public TelemetryPacket getPacket() {
        return packet;
    }

    public ContactRoute getRoute() {
        return route;
    }

    public int getScheduledAtStep() {
        return scheduledAtStep;
    }

    @Override
    public String toString() {
        return packet.getPriority()
                + " packet " + packet.getPacketId()
                + " from Sat-" + packet.getSourceId()
                + " | " + packet.getSizeKb() + "KB"
                + " | " + route.describe(packet.getSourceId())
                + " | deadline=" + packet.getDeadlineStep();
    }
}
