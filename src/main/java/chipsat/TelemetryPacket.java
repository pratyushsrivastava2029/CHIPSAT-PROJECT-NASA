package chipsat;

public class TelemetryPacket {
    private final int packetId;
    private final int sourceId;
    private final long createdAt;
    private final double temperatureC;
    private final double batteryPercent;
    private final double altitudeKm;
    private final TelemetryPriority priority;

    // okay this is where packet routing becomes an actual scheduling problem
    // 2 MB packet and a 50 KB health ping should not be treated the same
    // if the contact window only has like 700 KB left
    private final int sizeKb;

    // absolute simulation step by which this data stops being useful
    // especially for critical health telemetry, late can basically mean useless
    private final int deadlineStep;

    private final int ttlSteps;
    private int ageSteps;

    public TelemetryPacket(int packetId, int sourceId, long createdAt,
                           double temperatureC, double batteryPercent,
                           double altitudeKm, TelemetryPriority priority,
                           int ttlSteps) {
        this(packetId, sourceId, createdAt, temperatureC, batteryPercent,
                altitudeKm, priority, ttlSteps, 128, Integer.MAX_VALUE);
    }

    public TelemetryPacket(int packetId, int sourceId, long createdAt,
                           double temperatureC, double batteryPercent,
                           double altitudeKm, TelemetryPriority priority,
                           int ttlSteps, int sizeKb, int deadlineStep) {
        this.packetId = packetId;
        this.sourceId = sourceId;
        this.createdAt = createdAt;
        this.temperatureC = temperatureC;
        this.batteryPercent = batteryPercent;
        this.altitudeKm = altitudeKm;
        this.priority = priority;
        this.ttlSteps = ttlSteps;
        this.sizeKb = sizeKb;
        this.deadlineStep = deadlineStep;
        this.ageSteps = 0;
    }

    public int getPacketId() {
        return packetId;
    }

    public int getSourceId() {
        return sourceId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public double getBatteryPercent() {
        return batteryPercent;
    }

    public double getAltitudeKm() {
        return altitudeKm;
    }

    public TelemetryPriority getPriority() {
        return priority;
    }

    public int getSizeKb() {
        return sizeKb;
    }

    public int getDeadlineStep() {
        return deadlineStep;
    }

    public int getAgeSteps() {
        return ageSteps;
    }

    public boolean isExpired() {
        return ageSteps >= ttlSteps;
    }

    public boolean missesDeadlineAt(int step) {
        return step > deadlineStep;
    }

    public void ageOneStep() {
        ageSteps++;
    }

    @Override
    public String toString() {
        return String.format(
                "Packet %d | Sat-%d | %s | %dKB | deadline=%s | battery=%.1f%%",
                packetId,
                sourceId,
                priority,
                sizeKb,
                deadlineStep == Integer.MAX_VALUE ? "none" : deadlineStep,
                batteryPercent
        );
    }
}
