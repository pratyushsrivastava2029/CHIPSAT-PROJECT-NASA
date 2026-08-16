package chipsat;

import java.util.*;

public class Simulation {
    public static void main(String[] args) {
        long seed = 61L;
        Random telemetryRandom = new Random(seed);

        List<ChipSat> sats = Arrays.asList(
                new ChipSat(1, 12, 8, 96),
                new ChipSat(2, 30, 16, 88),
                new ChipSat(3, 48, 20, 82),
                new ChipSat(4, 64, 30, 77),
                new ChipSat(5, 45, 43, 91),
                new ChipSat(6, 25, 26, 5),
                new ChipSat(7, 70, 48, 94),
                new ChipSat(8, 52, 60, 74)
        );

        GroundStation ground = new GroundStation(0, 0, 24);
        TelemetryNetwork network = new TelemetryNetwork(
                sats,
                ground,
                27,
                0.04,
                seed
        );

        System.out.println("=== ChipSat Telemetry Mesh ===");
        System.out.println("Initial topology:");
        System.out.println(network.topologySummary());

        System.out.println("=== Phase 1: Normal telemetry ===");
        for (ChipSat sat : sats) {
            DeliveryResult result = network.sendPacket(
                    sat.generateTelemetry(telemetryRandom),
                    true
            );
            System.out.println(result);
        }

        System.out.println();
        System.out.println("=== Phase 2: Relay failure and alternate path ===");

        List<Integer> before = network.findLowestCostRoute(5);
        System.out.println("Sat-5 route before failure: "
                + TelemetryNetwork.formatRoute(before));

        System.out.println("Taking Sat-2 offline...");
        network.failSatellite(2);

        List<Integer> after = network.findLowestCostRoute(5);
        System.out.println("Sat-5 route after failure:  "
                + TelemetryNetwork.formatRoute(after));

        TelemetryPacket failureTest = sats.get(4).generateTelemetry(telemetryRandom);
        DeliveryResult afterFailure = network.sendPacket(failureTest, true);
        System.out.println(afterFailure);

        System.out.println();
        System.out.println("=== Phase 3: Dynamic topology ===");
        network.moveSatellites();
        System.out.println(network.topologySummary());

        for (int i = 0; i < 12; i++) {
            ChipSat sat = sats.get(telemetryRandom.nextInt(sats.size()));
            if (!sat.isOnline()) {
                continue;
            }

            DeliveryResult result = network.sendPacket(
                    sat.generateTelemetry(telemetryRandom),
                    true
            );
            System.out.println(result);
        }

        System.out.println();
        System.out.println("=== Final network metrics ===");
        System.out.println(network.getMetrics());
    }
}
