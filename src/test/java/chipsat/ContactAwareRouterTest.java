package chipsat;

public class ContactAwareRouterTest {
    public static void main(String[] args) {
        testFutureContactRoute();
        testDeadlineBlocksLateRoute();
        testCapacityReservationForcesAlternateRoute();
        System.out.println("Contact-aware tests passed.");
    }

    private static void testFutureContactRoute() {
        ContactPlan plan = MissionDemo.buildContactPlan();
        ContactAwareRouter router = new ContactAwareRouter();

        TelemetryPacket packet = new TelemetryPacket(
                1, 8, 0,
                10, 90, 410,
                TelemetryPriority.CRITICAL,
                6, 100, 6
        );

        ContactRoute route = router.findBestRoute(
                8, 0, packet, plan
        );

        if (route == null
                || route.getArrivalStep() > packet.getDeadlineStep()) {
            throw new AssertionError(
                    "expected future-contact route before deadline"
            );
        }
    }

    private static void testDeadlineBlocksLateRoute() {
        ContactPlan plan = MissionDemo.buildContactPlan();
        ContactAwareRouter router = new ContactAwareRouter();

        TelemetryPacket packet = new TelemetryPacket(
                2, 8, 0,
                10, 90, 410,
                TelemetryPriority.CRITICAL,
                2, 100, 2
        );

        if (router.findBestRoute(8, 0, packet, plan) != null) {
            throw new AssertionError(
                    "route should fail when ground contact is too late"
            );
        }
    }

    private static void testCapacityReservationForcesAlternateRoute() {
        ContactPlan plan = MissionDemo.buildContactPlan();
        ContactAwareRouter router = new ContactAwareRouter();

        TelemetryPacket first = new TelemetryPacket(
                3, 8, 0,
                10, 90, 410,
                TelemetryPriority.SCIENCE,
                12, 600, 11
        );

        ContactRoute firstRoute =
                router.findBestRoute(8, 0, first, plan);

        if (firstRoute == null) {
            throw new AssertionError("first packet should route");
        }

        router.reserveRoute(firstRoute, first);

        TelemetryPacket second = new TelemetryPacket(
                4, 8, 0,
                10, 90, 410,
                TelemetryPriority.SCIENCE,
                12, 600, 11
        );

        ContactRoute secondRoute =
                router.findBestRoute(8, 0, second, plan);

        if (secondRoute == null) {
            throw new AssertionError(
                    "second packet should find later alternate route"
            );
        }

        if (secondRoute.getArrivalStep()
                <= firstRoute.getArrivalStep()) {
            throw new AssertionError(
                    "reservation should force later path"
            );
        }
    }
}
