package chipsat;

public class ContactWindow {
    private final int fromId;
    private final int toId;
    private final int startStep;
    private final int endStep;
    private final int capacityKb;
    private int reservedKb;
    private final double energyCostPerKb;

    public ContactWindow(int fromId, int toId,
                         int startStep, int endStep,
                         int capacityKb, double energyCostPerKb) {
        this.fromId = fromId;
        this.toId = toId;
        this.startStep = startStep;
        this.endStep = endStep;
        this.capacityKb = capacityKb;
        this.energyCostPerKb = energyCostPerKb;
    }

    public int getFromId() { return fromId; }
    public int getToId() { return toId; }
    public int getStartStep() { return startStep; }
    public int getEndStep() { return endStep; }
    public int getCapacityKb() { return capacityKb; }
    public int getRemainingCapacityKb() { return capacityKb - reservedKb; }
    public double getEnergyCostPerKb() { return energyCostPerKb; }

    public boolean canCarry(int packetSizeKb) {
        return getRemainingCapacityKb() >= packetSizeKb;
    }

    public void reserve(int packetSizeKb) {
        if (!canCarry(packetSizeKb)) {
            throw new IllegalStateException("not enough contact capacity");
        }

        reservedKb += packetSizeKb;
    }

    public boolean involves(int nodeId) {
        return fromId == nodeId || toId == nodeId;
    }

    public int otherSide(int nodeId) {
        if (fromId == nodeId) {
            return toId;
        }

        if (toId == nodeId) {
            return fromId;
        }

        throw new IllegalArgumentException("node is not in this contact");
    }

    @Override
    public String toString() {
        return String.format(
                "%s <-> %s  steps[%d,%d]  remaining=%d/%dKB",
                label(fromId), label(toId),
                startStep, endStep,
                getRemainingCapacityKb(), capacityKb
        );
    }

    private String label(int id) {
        return id == GroundStation.ID ? "Ground" : "Sat-" + id;
    }
}
