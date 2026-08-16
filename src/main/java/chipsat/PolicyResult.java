package chipsat;

public class PolicyResult {
    private final String policy;
    private int total;
    private int delivered;
    private int criticalTotal;
    private int criticalOnTime;
    private int scienceKbDelivered;
    private int deadlineMisses;
    private double energyUsed;
    private int totalLatency;

    public PolicyResult(String policy) {
        this.policy = policy;
    }

    public void recordAttempt(TelemetryPacket packet) {
        total++;
        if (packet.getPriority() == TelemetryPriority.CRITICAL) {
            criticalTotal++;
        }
    }

    public void recordDelivery(TelemetryPacket packet, int latency, double energy) {
        delivered++;
        totalLatency += latency;
        energyUsed += energy;

        if (packet.getPriority() == TelemetryPriority.CRITICAL) {
            criticalOnTime++;
        }

        if (packet.getPriority() == TelemetryPriority.SCIENCE) {
            scienceKbDelivered += packet.getSizeKb();
        }
    }

    public void recordDeadlineMiss() {
        deadlineMisses++;
    }

    public String getPolicy() { return policy; }
    public int getTotal() { return total; }
    public int getDelivered() { return delivered; }
    public int getCriticalTotal() { return criticalTotal; }
    public int getCriticalOnTime() { return criticalOnTime; }
    public int getScienceKbDelivered() { return scienceKbDelivered; }
    public int getDeadlineMisses() { return deadlineMisses; }
    public double getEnergyUsed() { return energyUsed; }

    public double getDeliveryRate() {
        return total == 0 ? 0 : 100.0 * delivered / total;
    }

    public double getCriticalRate() {
        return criticalTotal == 0 ? 100 : 100.0 * criticalOnTime / criticalTotal;
    }

    public double getAverageLatency() {
        return delivered == 0 ? 0 : (double) totalLatency / delivered;
    }
}
