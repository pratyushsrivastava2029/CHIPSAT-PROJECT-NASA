package chipsat;

import java.util.PriorityQueue;
import java.util.Random;

public class ChipSat {
    private final int id;
    private double x;
    private double y;
    private double batteryPercent;
    private boolean online;
    private int nextPacketId;

    // okay so this is basically our little onboard buffer
    // if we cant reach ground rn we should NOT just delete the data lol
    // keep it here and try again once topology changes
    private final PriorityQueue<TelemetryPacket> outboundQueue;

    public ChipSat(int id, double x, double y, double batteryPercent) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.batteryPercent = batteryPercent;
        this.online = true;
        this.nextPacketId = 1;

        // critical data first, then older packets first if same priority
        outboundQueue = new PriorityQueue<>((a, b) -> {
            int priorityCompare = Integer.compare(
                    b.getPriority().getWeight(),
                    a.getPriority().getWeight()
            );

            if (priorityCompare != 0) {
                return priorityCompare;
            }

            return Integer.compare(b.getAgeSteps(), a.getAgeSteps());
        });
    }

    public TelemetryPacket generateTelemetry(Random random) {
        return generateTelemetry(random, 0);
    }

    public TelemetryPacket generateTelemetry(Random random, int currentStep) {
        double temperature = -15.0 + random.nextDouble() * 55.0;
        double altitude = 400.0 + random.nextDouble() * 25.0;

        batteryPercent = Math.max(
                0.0,
                batteryPercent - (0.1 + random.nextDouble() * 0.35)
        );

        if (batteryPercent == 0.0) {
            online = false;
        }

        TelemetryPriority priority = choosePriority(
                temperature,
                batteryPercent,
                random
        );

        // okay so im making these packet classes actually behave differently
        // health ping = tiny
        // science payload = chunky
        // critical = not huge but we need that thing on earth FAST
        int sizeKb;
        int deadline;

        if (priority == TelemetryPriority.CRITICAL) {
            sizeKb = 96 + random.nextInt(96);
            deadline = currentStep + 3;
        } else if (priority == TelemetryPriority.SCIENCE) {
            sizeKb = 700 + random.nextInt(900);
            deadline = currentStep + 12;
        } else {
            sizeKb = 32 + random.nextInt(64);
            deadline = currentStep + 8;
        }

        int ttl = priority == TelemetryPriority.CRITICAL ? 4 : 12;

        return new TelemetryPacket(
                nextPacketId++,
                id,
                System.currentTimeMillis(),
                temperature,
                batteryPercent,
                altitude,
                priority,
                ttl,
                sizeKb,
                deadline
        );
    }

    private TelemetryPriority choosePriority(double temperature,
                                             double battery,
                                             Random random) {
        // this is basically our "mission cares about this rn" check
        // thresholds are simulation choices, not me pretending these are flight values
        if (battery < 20.0 || temperature > 32.0) {
            return TelemetryPriority.CRITICAL;
        }

        if (random.nextDouble() < 0.35) {
            return TelemetryPriority.SCIENCE;
        }

        return TelemetryPriority.HEALTH;
    }

    public void queuePacket(TelemetryPacket packet) {
        outboundQueue.add(packet);
    }

    public TelemetryPacket peekQueuedPacket() {
        return outboundQueue.peek();
    }

    public TelemetryPacket pollQueuedPacket() {
        return outboundQueue.poll();
    }

    public int getQueueSize() {
        return outboundQueue.size();
    }

    public void ageQueuedPackets() {
        PriorityQueue<TelemetryPacket> refreshed = new PriorityQueue<>(
                outboundQueue.comparator()
        );

        while (!outboundQueue.isEmpty()) {
            TelemetryPacket packet = outboundQueue.poll();
            packet.ageOneStep();

            if (!packet.isExpired()) {
                refreshed.add(packet);
            }
        }

        outboundQueue.addAll(refreshed);
    }

    public void move(Random random) {
        x += random.nextDouble() * 6.0 - 3.0;
        y += random.nextDouble() * 6.0 - 3.0;
    }

    public double distanceTo(ChipSat other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public int getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getBatteryPercent() {
        return batteryPercent;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public boolean isOnline() {
        return online;
    }

    public void fail() {
        online = false;
    }

    public void recover() {
        if (batteryPercent > 0.0) {
            online = true;
        }
    }

    @Override
    public String toString() {
        return String.format("Sat-%d", id);
    }
}
