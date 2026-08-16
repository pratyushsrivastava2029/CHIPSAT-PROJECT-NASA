package chipsat;

import java.util.*;

public class TelemetryNetwork {

    // okay so this class is basically the reactive network view
    // like: what links exist RIGHT NOW and can i route through them
    // ContactAwareRouter is different because that guy reasons about future windows
    private final Map<Integer, ChipSat> satellites;
    private final Map<Integer, List<Link>> adjacency;
    private final GroundStation groundStation;
    private final double satelliteRange;
    private final double packetLossProbability;
    private final Random random;
    private final NetworkMetrics metrics;

    public TelemetryNetwork(List<ChipSat> satellites,
                            GroundStation groundStation,
                            double satelliteRange,
                            double packetLossProbability,
                            long seed) {
        this.satellites = new HashMap<>();
        for (ChipSat sat : satellites) {
            this.satellites.put(sat.getId(), sat);
        }

        this.groundStation = groundStation;
        this.satelliteRange = satelliteRange;
        this.packetLossProbability = packetLossProbability;
        this.random = new Random(seed);
        this.metrics = new NetworkMetrics();
        this.adjacency = new HashMap<>();

        rebuildLinks();
    }

    public void rebuildLinks() {
        // rebuild from scratch rn because swarm is tiny
        // if this were 10k sats then hell no, id update affected neighbors only
        adjacency.clear();
        adjacency.put(GroundStation.ID, new ArrayList<>());

        for (ChipSat sat : satellites.values()) {
            adjacency.put(sat.getId(), new ArrayList<>());
        }

        List<ChipSat> sats = new ArrayList<>(satellites.values());

        for (int i = 0; i < sats.size(); i++) {
            ChipSat a = sats.get(i);
            if (!a.isOnline()) {
                continue;
            }

            if (groundStation.distanceTo(a) <= groundStation.getCommunicationRange()) {
                double cost = linkCost(groundStation.distanceTo(a), a.getBatteryPercent());
                adjacency.get(a.getId()).add(new Link(GroundStation.ID, cost));
                adjacency.get(GroundStation.ID).add(new Link(a.getId(), cost));
            }

            for (int j = i + 1; j < sats.size(); j++) {
                ChipSat b = sats.get(j);
                if (!b.isOnline()) {
                    continue;
                }

                double distance = a.distanceTo(b);
                if (distance <= satelliteRange) {
                    double averageBattery = (a.getBatteryPercent() + b.getBatteryPercent()) / 2.0;
                    double cost = linkCost(distance, averageBattery);

                    adjacency.get(a.getId()).add(new Link(b.getId(), cost));
                    adjacency.get(b.getId()).add(new Link(a.getId(), cost));
                }
            }
        }
    }

    private double linkCost(double distance, double batteryPercent) {
        double batteryPenalty = (100.0 - batteryPercent) / 100.0;
        return distance + 20.0 * batteryPenalty;
    }

    public List<Integer> findShortestHopRoute(int sourceId) {
        if (!satellites.containsKey(sourceId) || !satellites.get(sourceId).isOnline()) {
            return Collections.emptyList();
        }

        Queue<Integer> queue = new ArrayDeque<>();
        Map<Integer, Integer> parent = new HashMap<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(sourceId);
        visited.add(sourceId);

        while (!queue.isEmpty()) {
            int current = queue.remove();

            if (current == GroundStation.ID) {
                return buildRoute(parent, sourceId);
            }

            for (Link link : adjacency.getOrDefault(current, Collections.emptyList())) {
                int next = link.getDestinationId();
                if (!visited.contains(next)) {
                    visited.add(next);
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }

        return Collections.emptyList();
    }

    public List<Integer> findLowestCostRoute(int sourceId) {
        if (!satellites.containsKey(sourceId) || !satellites.get(sourceId).isOnline()) {
            return Collections.emptyList();
        }

        Map<Integer, Double> distance = new HashMap<>();
        Map<Integer, Integer> parent = new HashMap<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>();

        for (Integer node : adjacency.keySet()) {
            distance.put(node, Double.POSITIVE_INFINITY);
        }

        distance.put(sourceId, 0.0);
        pq.add(new NodeDistance(sourceId, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.remove();

            if (current.cost > distance.get(current.nodeId)) {
                continue;
            }

            if (current.nodeId == GroundStation.ID) {
                return buildRoute(parent, sourceId);
            }

            for (Link link : adjacency.getOrDefault(current.nodeId, Collections.emptyList())) {
                int next = link.getDestinationId();
                double newCost = current.cost + link.getCost();

                if (newCost < distance.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    distance.put(next, newCost);
                    parent.put(next, current.nodeId);
                    pq.add(new NodeDistance(next, newCost));
                }
            }
        }

        return Collections.emptyList();
    }

    private List<Integer> buildRoute(Map<Integer, Integer> parent, int sourceId) {
        List<Integer> route = new ArrayList<>();
        int current = GroundStation.ID;
        route.add(current);

        while (current != sourceId) {
            Integer previous = parent.get(current);
            if (previous == null) {
                return Collections.emptyList();
            }
            current = previous;
            route.add(current);
        }

        Collections.reverse(route);
        return route;
    }

    public DeliveryResult sendPacket(TelemetryPacket packet, boolean lowestCostRouting) {
        metrics.recordSent();

        List<Integer> route = lowestCostRouting
                ? findLowestCostRoute(packet.getSourceId())
                : findShortestHopRoute(packet.getSourceId());

        if (route.isEmpty()) {
            metrics.recordDropped();
            return DeliveryResult.failed(packet, "No route to ground station");
        }

        List<Integer> attemptedRoute = new ArrayList<>(route);

        for (int i = 0; i < route.size() - 1; i++) {
            int current = route.get(i);
            int next = route.get(i + 1);

            if (current != GroundStation.ID && !satellites.get(current).isOnline()) {
                return reroute(packet, current, attemptedRoute, lowestCostRouting);
            }

            if (next != GroundStation.ID && !satellites.get(next).isOnline()) {
                return reroute(packet, current, attemptedRoute, lowestCostRouting);
            }

            if (random.nextDouble() < packetLossProbability) {
                metrics.recordDropped();
                return DeliveryResult.failed(packet,
                        "Packet lost on link " + label(current) + " -> " + label(next));
            }
        }

        metrics.recordDelivered(route.size() - 1);
        return DeliveryResult.delivered(packet, route);
    }

    private DeliveryResult reroute(TelemetryPacket packet,
                                   int currentNode,
                                   List<Integer> originalRoute,
                                   boolean lowestCostRouting) {
        rebuildLinks();
        metrics.recordReroute();

        List<Integer> newRoute = currentNode == GroundStation.ID
                ? Collections.singletonList(GroundStation.ID)
                : (lowestCostRouting
                    ? findLowestCostRoute(currentNode)
                    : findShortestHopRoute(currentNode));

        if (newRoute.isEmpty()) {
            metrics.recordDropped();
            return DeliveryResult.failed(packet,
                    "Route failed and no alternate path exists. Original route: "
                            + formatRoute(originalRoute));
        }

        for (int i = 0; i < newRoute.size() - 1; i++) {
            if (random.nextDouble() < packetLossProbability) {
                metrics.recordDropped();
                return DeliveryResult.failed(packet,
                        "Packet lost during reroute on "
                                + label(newRoute.get(i)) + " -> " + label(newRoute.get(i + 1)));
            }
        }

        metrics.recordDelivered(newRoute.size() - 1);
        return DeliveryResult.rerouted(packet, originalRoute, newRoute);
    }

    public void failSatellite(int id) {
        ChipSat sat = satellites.get(id);
        if (sat != null) {
            sat.fail();
            rebuildLinks();
        }
    }

    public void recoverSatellite(int id) {
        ChipSat sat = satellites.get(id);
        if (sat != null) {
            sat.recover();
            rebuildLinks();
        }
    }


    public MissionDecision handleTelemetry(TelemetryPacket packet,
                                           boolean lowestCostRouting) {
        ChipSat source = satellites.get(packet.getSourceId());

        if (source == null || !source.isOnline()) {
            return MissionDecision.dropped(packet, "Source satellite is offline");
        }

        List<Integer> route = lowestCostRouting
                ? findLowestCostRoute(packet.getSourceId())
                : findShortestHopRoute(packet.getSourceId());

        if (route.isEmpty()) {
            // this is the actual networking problem imo
            // no path does NOT mean the data is useless
            // satellite networks are disconnected all the time so hold it locally
            source.queuePacket(packet);

            return MissionDecision.stored(
                    packet,
                    "No current path to Ground. Stored onboard Sat-"
                            + source.getId()
                            + " queue=" + source.getQueueSize()
            );
        }

        DeliveryResult result = sendPacket(packet, lowestCostRouting);

        if (result.isDelivered()) {
            return MissionDecision.delivered(packet, result.getMessage());
        }

        // packet loss is different from "no route"
        // for now we let the next telemetry cycle retry by storing it
        if (!packet.isExpired()) {
            source.queuePacket(packet);
            return MissionDecision.stored(packet,
                    "Transmission failed. Packet queued for retry.");
        }

        return MissionDecision.dropped(packet, result.getMessage());
    }

    public List<MissionDecision> flushStoredTelemetry(boolean lowestCostRouting) {
        List<MissionDecision> decisions = new ArrayList<>();

        // one packet per sat per cycle = fake but intentional bandwidth constraint
        // otherwise once a path opens every node instantly dumps everything
        for (ChipSat sat : satellites.values()) {
            if (!sat.isOnline()) {
                continue;
            }

            sat.ageQueuedPackets();

            TelemetryPacket packet = sat.peekQueuedPacket();
            if (packet == null) {
                continue;
            }

            List<Integer> route = lowestCostRouting
                    ? findLowestCostRoute(sat.getId())
                    : findShortestHopRoute(sat.getId());

            if (route.isEmpty()) {
                decisions.add(MissionDecision.stored(
                        packet,
                        "Sat-" + sat.getId()
                                + " still disconnected; keeping packet onboard."
                ));
                continue;
            }

            packet = sat.pollQueuedPacket();
            DeliveryResult result = sendPacket(packet, lowestCostRouting);

            if (result.isDelivered()) {
                decisions.add(MissionDecision.delivered(
                        packet,
                        "Contact restored: " + result.getMessage()
                ));
            } else if (!packet.isExpired()) {
                sat.queuePacket(packet);
                decisions.add(MissionDecision.stored(
                        packet,
                        "Retry failed; packet returned to queue."
                ));
            } else {
                decisions.add(MissionDecision.dropped(
                        packet,
                        "Packet expired before successful delivery."
                ));
            }
        }

        return decisions;
    }

    public int getTotalQueuedPackets() {
        int total = 0;

        for (ChipSat sat : satellites.values()) {
            total += sat.getQueueSize();
        }

        return total;
    }

    public void moveSatellites() {
        for (ChipSat sat : satellites.values()) {
            if (sat.isOnline()) {
                sat.move(random);
            }
        }
        rebuildLinks();
    }


    public Collection<ChipSat> getSatellites() {
        return Collections.unmodifiableCollection(satellites.values());
    }

    public ChipSat getSatellite(int id) {
        return satellites.get(id);
    }

    public Map<Integer, List<Link>> getAdjacencySnapshot() {
        Map<Integer, List<Link>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Link>> entry : adjacency.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    public GroundStation getGroundStation() {
        return groundStation;
    }

    public double getSatelliteRange() {
        return satelliteRange;
    }

    public NetworkMetrics getMetrics() {
        return metrics;
    }

    public String topologySummary() {
        StringBuilder out = new StringBuilder();
        List<Integer> ids = new ArrayList<>(adjacency.keySet());
        Collections.sort(ids);

        for (int id : ids) {
            out.append(label(id)).append(" -> ");

            List<String> neighbors = new ArrayList<>();
            for (Link link : adjacency.getOrDefault(id, Collections.emptyList())) {
                neighbors.add(label(link.getDestinationId()));
            }

            out.append(neighbors).append(System.lineSeparator());
        }

        return out.toString();
    }

    public static String formatRoute(List<Integer> route) {
        List<String> labels = new ArrayList<>();
        for (int node : route) {
            labels.add(label(node));
        }
        return String.join(" -> ", labels);
    }

    private static String label(int id) {
        return id == GroundStation.ID ? "Ground" : "Sat-" + id;
    }

    private static class NodeDistance implements Comparable<NodeDistance> {
        private final int nodeId;
        private final double cost;

        private NodeDistance(int nodeId, double cost) {
            this.nodeId = nodeId;
            this.cost = cost;
        }

        @Override
        public int compareTo(NodeDistance other) {
            return Double.compare(cost, other.cost);
        }
    }
}
