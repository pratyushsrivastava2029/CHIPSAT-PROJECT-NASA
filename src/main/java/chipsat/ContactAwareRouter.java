package chipsat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ContactAwareRouter {

    public ContactRoute findBestRoute(int sourceId,
                                      int currentStep,
                                      TelemetryPacket packet,
                                      ContactPlan plan) {

        // okay THIS is the thing thats different from regular shortest path
        // graph edge isnt just "there"
        // every edge has a time window + capacity so we are routing in space AND time
        Map<Integer, State> best = new HashMap<>();
        PriorityQueue<State> pq = new PriorityQueue<>();

        State start = new State(
                sourceId,
                currentStep,
                0.0,
                new ArrayList<>()
        );

        best.put(sourceId, start);
        pq.add(start);

        while (!pq.isEmpty()) {
            State current = pq.remove();

            State known = best.get(current.nodeId);
            if (known != null && current.compareTo(known) > 0) {
                continue;
            }

            if (current.nodeId == GroundStation.ID) {
                return new ContactRoute(
                        current.path,
                        current.arrivalStep,
                        current.energyCost
                );
            }

            for (ContactWindow contact : plan.contactsFrom(current.nodeId)) {
                int nextNode = contact.otherSide(current.nodeId);

                // say packet gets here step 5 but radio window ended step 3
                // that edge is useless even though topology-wise nodes "connect"
                int usableStep = Math.max(
                        current.arrivalStep,
                        contact.getStartStep()
                );

                if (usableStep > contact.getEndStep()) {
                    continue;
                }

                if (!contact.canCarry(packet.getSizeKb())) {
                    continue;
                }

                int nextArrival = usableStep + 1;

                if (packet.missesDeadlineAt(nextArrival)) {
                    continue;
                }

                double nextEnergy = current.energyCost
                        + packet.getSizeKb() * contact.getEnergyCostPerKb();

                List<ContactWindow> path =
                        new ArrayList<>(current.path);
                path.add(contact);

                State candidate = new State(
                        nextNode,
                        nextArrival,
                        nextEnergy,
                        path
                );

                State old = best.get(nextNode);

                // earliest arrival first
                // if same arrival then use less radio energy
                if (old == null || candidate.compareTo(old) < 0) {
                    best.put(nextNode, candidate);
                    pq.add(candidate);
                }
            }
        }

        return null;
    }

    public void reserveRoute(ContactRoute route,
                             TelemetryPacket packet) {
        for (ContactWindow contact : route.getContacts()) {
            contact.reserve(packet.getSizeKb());
        }
    }

    private static class State implements Comparable<State> {
        private final int nodeId;
        private final int arrivalStep;
        private final double energyCost;
        private final List<ContactWindow> path;

        private State(int nodeId, int arrivalStep,
                      double energyCost,
                      List<ContactWindow> path) {
            this.nodeId = nodeId;
            this.arrivalStep = arrivalStep;
            this.energyCost = energyCost;
            this.path = path;
        }

        @Override
        public int compareTo(State other) {
            int time = Integer.compare(
                    arrivalStep,
                    other.arrivalStep
            );

            if (time != 0) {
                return time;
            }

            return Double.compare(
                    energyCost,
                    other.energyCost
            );
        }
    }
}
