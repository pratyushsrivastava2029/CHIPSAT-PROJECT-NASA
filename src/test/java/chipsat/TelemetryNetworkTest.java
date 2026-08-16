package chipsat;

import java.util.*;

public class TelemetryNetworkTest {
    public static void main(String[] args) {
        testMultiHopRoute();
        testNoRouteAfterFailure();
        testLowestCostRoute();
        testStoreAndForward();
        System.out.println("All tests passed.");
    }

    private static void testMultiHopRoute() {
        List<ChipSat> sats = Arrays.asList(
                new ChipSat(1, 10, 0, 100),
                new ChipSat(2, 25, 0, 100),
                new ChipSat(3, 40, 0, 100)
        );

        TelemetryNetwork network = new TelemetryNetwork(
                sats,
                new GroundStation(0, 0, 12),
                16,
                0.0,
                1
        );

        List<Integer> route = network.findShortestHopRoute(3);
        assertEquals(Arrays.asList(3, 2, 1, 0), route, "multi-hop BFS route");
    }

    private static void testNoRouteAfterFailure() {
        List<ChipSat> sats = Arrays.asList(
                new ChipSat(1, 10, 0, 100),
                new ChipSat(2, 25, 0, 100),
                new ChipSat(3, 40, 0, 100)
        );

        TelemetryNetwork network = new TelemetryNetwork(
                sats,
                new GroundStation(0, 0, 12),
                16,
                0.0,
                1
        );

        network.failSatellite(2);
        List<Integer> route = network.findShortestHopRoute(3);
        assertEquals(Collections.emptyList(), route, "route should disappear after relay failure");
    }

    private static void testLowestCostRoute() {
        List<ChipSat> sats = Arrays.asList(
                new ChipSat(1, 10, 0, 95),
                new ChipSat(2, 10, 10, 95),
                new ChipSat(3, 20, 10, 95)
        );

        TelemetryNetwork network = new TelemetryNetwork(
                sats,
                new GroundStation(0, 0, 12),
                15,
                0.0,
                2
        );

        List<Integer> route = network.findLowestCostRoute(3);
        if (route.isEmpty() || route.get(0) != 3 || route.get(route.size() - 1) != 0) {
            throw new AssertionError("Dijkstra route should reach ground");
        }
    }


    private static void testStoreAndForward() {
        ChipSat sat = new ChipSat(1, 50, 0, 100);

        TelemetryNetwork network = new TelemetryNetwork(
                Collections.singletonList(sat),
                new GroundStation(0, 0, 10),
                10,
                0.0,
                4
        );

        TelemetryPacket packet = new TelemetryPacket(
                1,
                1,
                System.currentTimeMillis(),
                10.0,
                90.0,
                410.0,
                TelemetryPriority.SCIENCE,
                8
        );

        MissionDecision first = network.handleTelemetry(packet, true);

        if (first.getStatus() != MissionDecision.Status.STORED) {
            throw new AssertionError("disconnected packet should be stored");
        }

        sat.setPosition(8, 0);
        network.rebuildLinks();

        List<MissionDecision> retried = network.flushStoredTelemetry(true);

        if (retried.isEmpty()
                || retried.get(0).getStatus() != MissionDecision.Status.DELIVERED) {
            throw new AssertionError("stored packet should forward when contact returns");
        }
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    name + " failed. Expected " + expected + " but got " + actual
            );
        }
    }
}
