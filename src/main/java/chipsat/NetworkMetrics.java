package chipsat;

public class NetworkMetrics {
    private int sent;
    private int delivered;
    private int dropped;
    private int rerouted;
    private int totalDeliveredHops;

    public void recordSent() {
        sent++;
    }

    public void recordDelivered(int hops) {
        delivered++;
        totalDeliveredHops += hops;
    }

    public void recordDropped() {
        dropped++;
    }

    public void recordReroute() {
        rerouted++;
    }

    public int getSent() {
        return sent;
    }

    public int getDelivered() {
        return delivered;
    }

    public int getDropped() {
        return dropped;
    }

    public double getDeliveryRate() {
        if (sent == 0) {
            return 0.0;
        }
        return 100.0 * delivered / sent;
    }

    public double getAverageHops() {
        if (delivered == 0) {
            return 0.0;
        }
        return (double) totalDeliveredHops / delivered;
    }

    @Override
    public String toString() {
        return String.format(
                "sent=%d delivered=%d dropped=%d rerouted=%d deliveryRate=%.1f%% avgHops=%.2f",
                sent, delivered, dropped, rerouted, getDeliveryRate(), getAverageHops()
        );
    }
}
