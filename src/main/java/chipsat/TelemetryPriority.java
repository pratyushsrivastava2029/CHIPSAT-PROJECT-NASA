package chipsat;

public enum TelemetryPriority {
    CRITICAL(3),
    SCIENCE(2),
    HEALTH(1);

    private final int weight;

    TelemetryPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
