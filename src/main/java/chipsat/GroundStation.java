package chipsat;

public class GroundStation {
    public static final int ID = 0;

    private final double x;
    private final double y;
    private final double communicationRange;

    public GroundStation(double x, double y, double communicationRange) {
        this.x = x;
        this.y = y;
        this.communicationRange = communicationRange;
    }

    public double distanceTo(ChipSat sat) {
        double dx = x - sat.getX();
        double dy = y - sat.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double getCommunicationRange() {
        return communicationRange;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
