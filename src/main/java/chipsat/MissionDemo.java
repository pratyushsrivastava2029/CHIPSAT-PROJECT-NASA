package chipsat;

import java.util.List;

public class MissionDemo {
    public static void main(String[] args) {
        ContactPlan plan = buildContactPlan();
        MissionScheduler scheduler = new MissionScheduler(plan);

        System.out.println("=== ChipSat Contact-Aware Mission Scheduler ===");
        System.out.println();
        System.out.println(
                "Goal: get highest-value telemetry to Ground before deadlines "
                        + "through future capacity-limited contacts."
        );
        System.out.println();

        TelemetryPacket critical = new TelemetryPacket(
                1, 8, System.currentTimeMillis(),
                38.0, 18.0, 410.0,
                TelemetryPriority.CRITICAL,
                4, 120, 6
        );

        TelemetryPacket science = new TelemetryPacket(
                2, 8, System.currentTimeMillis(),
                10.0, 74.0, 410.0,
                TelemetryPriority.SCIENCE,
                12, 900, 11
        );

        TelemetryPacket health = new TelemetryPacket(
                3, 6, System.currentTimeMillis(),
                12.0, 65.0, 410.0,
                TelemetryPriority.HEALTH,
                12, 70, 9
        );

        // submit in "wrong" order on purpose
        // scheduler itself should figure out what deserves scarce contact capacity
        scheduler.submit(science);
        scheduler.submit(health);
        scheduler.submit(critical);

        List<ScheduledDelivery> deliveries =
                scheduler.schedule(0);

        System.out.println("Scheduled:");
        for (ScheduledDelivery delivery : deliveries) {
            System.out.println("  " + delivery);
        }

        System.out.println();
        System.out.println(
                "Still waiting because no feasible capacity/deadline route: "
                        + scheduler.getPendingCount()
        );

        System.out.println();
        System.out.println("Remaining future contact capacity:");
        for (ContactWindow contact : plan.getContacts()) {
            System.out.println("  " + contact);
        }
    }

    public static ContactPlan buildContactPlan() {
        ContactPlan plan = new ContactPlan();

        // think of each one as a radio window we know is coming
        // not all these links exist at once which is the whole point
        plan.addContact(new ContactWindow(8, 5, 0, 2, 1100, 0.010));
        plan.addContact(new ContactWindow(5, 3, 2, 4, 1000, 0.012));
        plan.addContact(new ContactWindow(3, 1, 3, 5, 900, 0.015));
        plan.addContact(new ContactWindow(1, 0, 4, 6, 650, 0.020));

        // slower alternate path but way more capacity
        plan.addContact(new ContactWindow(8, 7, 3, 5, 1600, 0.014));
        plan.addContact(new ContactWindow(7, 4, 5, 7, 1500, 0.014));
        plan.addContact(new ContactWindow(4, 2, 7, 9, 1400, 0.016));
        plan.addContact(new ContactWindow(2, 0, 9, 11, 1300, 0.019));

        plan.addContact(new ContactWindow(6, 3, 1, 3, 250, 0.011));

        return plan;
    }
}
