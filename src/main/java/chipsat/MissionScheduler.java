package chipsat;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class MissionScheduler {
    private final ContactPlan contactPlan;
    private final ContactAwareRouter router;
    private final PriorityQueue<TelemetryPacket> pending;

    public MissionScheduler(ContactPlan contactPlan) {
        this.contactPlan = contactPlan;
        this.router = new ContactAwareRouter();

        // this is basically our mission priority order
        // emergency before science, earlier deadline before later,
        // then smaller packet as tie breaker so a short window can still be useful
        pending = new PriorityQueue<>((a, b) -> {
            int priority = Integer.compare(
                    b.getPriority().getWeight(),
                    a.getPriority().getWeight()
            );

            if (priority != 0) {
                return priority;
            }

            int deadline = Integer.compare(
                    a.getDeadlineStep(),
                    b.getDeadlineStep()
            );

            if (deadline != 0) {
                return deadline;
            }

            return Integer.compare(
                    a.getSizeKb(),
                    b.getSizeKb()
            );
        });
    }

    public void submit(TelemetryPacket packet) {
        pending.add(packet);
    }

    public List<ScheduledDelivery> schedule(int currentStep) {
        List<ScheduledDelivery> scheduled = new ArrayList<>();
        List<TelemetryPacket> stillWaiting = new ArrayList<>();

        while (!pending.isEmpty()) {
            TelemetryPacket packet = pending.poll();

            if (packet.missesDeadlineAt(currentStep)) {
                continue;
            }

            ContactRoute route = router.findBestRoute(
                    packet.getSourceId(),
                    currentStep,
                    packet,
                    contactPlan
            );

            if (route == null) {
                // dont drop it immediately
                // a packet can be unschedulable rn simply because higher priority stuff
                // already ate capacity, so keep it waiting while deadline allows
                stillWaiting.add(packet);
                continue;
            }

            // THIS reservation is important
            // after packet A claims 500 KB on a future downlink,
            // packet B must solve a different problem with only remaining capacity
            router.reserveRoute(route, packet);

            scheduled.add(
                    new ScheduledDelivery(
                            packet,
                            route,
                            currentStep
                    )
            );
        }

        pending.addAll(stillWaiting);
        return scheduled;
    }

    public int getPendingCount() {
        return pending.size();
    }
}
