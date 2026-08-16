package chipsat;

public class Link implements Comparable<Link> {
    private final int destinationId;
    private final double cost;

    public Link(int destinationId, double cost) {
        this.destinationId = destinationId;
        this.cost = cost;
    }

    public int getDestinationId() {
        return destinationId;
    }

    public double getCost() {
        return cost;
    }

    @Override
    public int compareTo(Link other) {
        return Double.compare(cost, other.cost);
    }
}
