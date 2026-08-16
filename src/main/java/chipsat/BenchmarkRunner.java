package chipsat;

import java.util.*;

public class BenchmarkRunner {
    private static final int GROUND = GroundStation.ID;

    public static void main(String[] args) {
        List<PacketSpec> workload = buildWorkload(180, 2027L);

        PolicyResult reactive = runReactive(workload);
        PolicyResult greedy = runGreedyCurrentCost(workload);
        PolicyResult contactAware = runContactAware(workload);

        System.out.println("=== Routing Policy Benchmark ===");
        System.out.println("same seeded workload, same contact opportunities");
        System.out.println();

        printHeader();
        printResult(reactive);
        printResult(greedy);
        printResult(contactAware);

        System.out.println();
        System.out.println("Interpretation:");
        System.out.println("- Immediate Route transmits only when a complete end-to-end route exists right now.");
        System.out.println("- Energy-Aware Now chooses a lower-energy route, but still cannot plan through future contacts.");
        System.out.println("- Mission Scheduler plans through future contact windows and reserves scarce capacity by mission priority/deadline.");
    }

    public static List<PolicyResult> runAll() {
        List<PacketSpec> workload = buildWorkload(180, 2027L);
        return Arrays.asList(
                runReactive(workload),
                runGreedyCurrentCost(workload),
                runContactAware(workload)
        );
    }

    private static PolicyResult runReactive(List<PacketSpec> workload) {
        ContactPlan plan = benchmarkPlan();
        PolicyResult result = new PolicyResult("Immediate Route");

        for (PacketSpec spec : workload) {
            TelemetryPacket packet = spec.packet();
            result.recordAttempt(packet);

            // okay intentionally dumb baseline:
            // if i cannot see a COMPLETE path using links active this exact step, packet loses
            RouteChoice route = currentRoute(
                    spec.sourceId, spec.createdStep, packet, plan, false
            );

            if (route == null || route.arrival > packet.getDeadlineStep()) {
                result.recordDeadlineMiss();
            } else {
                reserve(route.contacts, packet);
                result.recordDelivery(packet,
                        route.arrival - spec.createdStep,
                        route.energy);
            }
        }

        return result;
    }

    private static PolicyResult runGreedyCurrentCost(List<PacketSpec> workload) {
        ContactPlan plan = benchmarkPlan();
        PolicyResult result = new PolicyResult("Energy-Aware Now");

        for (PacketSpec spec : workload) {
            TelemetryPacket packet = spec.packet();
            result.recordAttempt(packet);

            RouteChoice route = currentRoute(
                    spec.sourceId, spec.createdStep, packet, plan, true
            );

            if (route == null || route.arrival > packet.getDeadlineStep()) {
                result.recordDeadlineMiss();
            } else {
                reserve(route.contacts, packet);
                result.recordDelivery(packet,
                        route.arrival - spec.createdStep,
                        route.energy);
            }
        }

        return result;
    }

    private static PolicyResult runContactAware(List<PacketSpec> workload) {
        ContactPlan plan = benchmarkPlan();
        ContactAwareRouter router = new ContactAwareRouter();
        PolicyResult result = new PolicyResult("Mission Scheduler");

        // same basic idea as MissionScheduler:
        // urgent packets get first shot at scarce future bandwidth
        List<PacketSpec> ordered = new ArrayList<>(workload);
        ordered.sort((a, b) -> {
            int p = Integer.compare(
                    b.packet().getPriority().getWeight(),
                    a.packet().getPriority().getWeight()
            );
            if (p != 0) return p;

            int d = Integer.compare(
                    a.packet().getDeadlineStep(),
                    b.packet().getDeadlineStep()
            );
            if (d != 0) return d;

            return Integer.compare(
                    a.packet().getSizeKb(),
                    b.packet().getSizeKb()
            );
        });

        for (PacketSpec spec : ordered) {
            TelemetryPacket packet = spec.packet();
            result.recordAttempt(packet);

            ContactRoute route = router.findBestRoute(
                    spec.sourceId,
                    spec.createdStep,
                    packet,
                    plan
            );

            if (route == null
                    || route.getArrivalStep() > packet.getDeadlineStep()) {
                result.recordDeadlineMiss();
                continue;
            }

            router.reserveRoute(route, packet);
            result.recordDelivery(
                    packet,
                    route.getArrivalStep() - spec.createdStep,
                    route.getEnergyCost()
            );
        }

        return result;
    }

    private static RouteChoice currentRoute(int source,
                                            int step,
                                            TelemetryPacket packet,
                                            ContactPlan plan,
                                            boolean energyWeighted) {
        // active contacts become a normal graph snapshot
        Map<Integer, List<ContactWindow>> graph = new HashMap<>();

        for (ContactWindow c : plan.activeAt(step)) {
            if (!c.canCarry(packet.getSizeKb())) continue;

            graph.computeIfAbsent(c.getFromId(), k -> new ArrayList<>()).add(c);
            graph.computeIfAbsent(c.getToId(), k -> new ArrayList<>()).add(c);
        }

        class State implements Comparable<State> {
            int node;
            double cost;
            List<ContactWindow> path;

            State(int node, double cost, List<ContactWindow> path) {
                this.node = node;
                this.cost = cost;
                this.path = path;
            }

            public int compareTo(State other) {
                return Double.compare(cost, other.cost);
            }
        }

        PriorityQueue<State> pq = new PriorityQueue<>();
        Map<Integer, Double> best = new HashMap<>();
        pq.add(new State(source, 0, new ArrayList<>()));
        best.put(source, 0.0);

        while (!pq.isEmpty()) {
            State cur = pq.remove();

            if (cur.node == GROUND) {
                double energy = 0;
                for (ContactWindow c : cur.path) {
                    energy += packet.getSizeKb() * c.getEnergyCostPerKb();
                }
                return new RouteChoice(
                        cur.path,
                        step + cur.path.size(),
                        energy
                );
            }

            if (cur.cost > best.getOrDefault(cur.node, Double.MAX_VALUE)) {
                continue;
            }

            for (ContactWindow c : graph.getOrDefault(cur.node, Collections.emptyList())) {
                int next = c.otherSide(cur.node);
                double edgeCost = energyWeighted
                        ? packet.getSizeKb() * c.getEnergyCostPerKb()
                        : 1.0;

                double nextCost = cur.cost + edgeCost;

                if (nextCost < best.getOrDefault(next, Double.MAX_VALUE)) {
                    best.put(next, nextCost);
                    List<ContactWindow> nextPath = new ArrayList<>(cur.path);
                    nextPath.add(c);
                    pq.add(new State(next, nextCost, nextPath));
                }
            }
        }

        return null;
    }

    private static void reserve(List<ContactWindow> contacts,
                                TelemetryPacket packet) {
        for (ContactWindow c : contacts) {
            c.reserve(packet.getSizeKb());
        }
    }

    private static List<PacketSpec> buildWorkload(int count, long seed) {
        Random r = new Random(seed);
        List<PacketSpec> packets = new ArrayList<>();

        int[] sources = {5, 6, 7, 8};

        for (int i = 0; i < count; i++) {
            int created = r.nextInt(18);
            int source = sources[r.nextInt(sources.length)];

            double roll = r.nextDouble();
            TelemetryPriority priority;
            int size;
            int deadline;

            if (roll < 0.18) {
                priority = TelemetryPriority.CRITICAL;
                size = 60 + r.nextInt(100);
                deadline = created + 5;
            } else if (roll < 0.55) {
                priority = TelemetryPriority.SCIENCE;
                size = 300 + r.nextInt(500);
                deadline = created + 12;
            } else {
                priority = TelemetryPriority.HEALTH;
                size = 30 + r.nextInt(70);
                deadline = created + 9;
            }

            packets.add(new PacketSpec(
                    source,
                    created,
                    new TelemetryPacket(
                            i + 1,
                            source,
                            i,
                            10,
                            80,
                            410,
                            priority,
                            20,
                            size,
                            deadline
                    )
            ));
        }

        return packets;
    }

    public static ContactPlan benchmarkPlan() {
        ContactPlan plan = new ContactPlan();

        // repeating-ish passes: short current connectivity, then gaps, then later opportunities
        // capacities are intentionally finite so policy choices can hurt future packets
        for (int base = 0; base <= 24; base += 6) {
            plan.addContact(new ContactWindow(8, 5, base, base + 1, 2600, 0.010));
            plan.addContact(new ContactWindow(7, 5, base + 1, base + 2, 2400, 0.011));
            plan.addContact(new ContactWindow(6, 3, base + 1, base + 3, 2200, 0.010));
            plan.addContact(new ContactWindow(5, 3, base + 2, base + 3, 2500, 0.012));
            plan.addContact(new ContactWindow(3, 1, base + 3, base + 4, 2300, 0.014));
            plan.addContact(new ContactWindow(1, GROUND, base + 4, base + 5, 1800, 0.019));

            // alternate higher-capacity path that opens later
            plan.addContact(new ContactWindow(8, 7, base + 2, base + 4, 3000, 0.013));
            plan.addContact(new ContactWindow(7, 4, base + 3, base + 5, 3000, 0.014));
            plan.addContact(new ContactWindow(4, 2, base + 4, base + 6, 2800, 0.015));
            plan.addContact(new ContactWindow(2, GROUND, base + 5, base + 7, 2600, 0.018));
        }

        return plan;
    }

    private static void printHeader() {
        System.out.printf(
                "%-18s %9s %12s %13s %12s %12s %12s%n",
                "POLICY", "DELIVERY", "CRIT ONTIME",
                "SCIENCE MB", "AVG LATENCY",
                "MISSES", "ENERGY"
        );
    }

    private static void printResult(PolicyResult r) {
        System.out.printf(
                "%-18s %8.1f%% %11.1f%% %12.2f %12.2f %12d %12.1f%n",
                r.getPolicy(),
                r.getDeliveryRate(),
                r.getCriticalRate(),
                r.getScienceKbDelivered() / 1024.0,
                r.getAverageLatency(),
                r.getDeadlineMisses(),
                r.getEnergyUsed()
        );
    }

    private static class RouteChoice {
        List<ContactWindow> contacts;
        int arrival;
        double energy;

        RouteChoice(List<ContactWindow> contacts,
                    int arrival,
                    double energy) {
            this.contacts = contacts;
            this.arrival = arrival;
            this.energy = energy;
        }
    }

    private static class PacketSpec {
        int sourceId;
        int createdStep;
        TelemetryPacket packet;

        PacketSpec(int sourceId,
                   int createdStep,
                   TelemetryPacket packet) {
            this.sourceId = sourceId;
            this.createdStep = createdStep;
            this.packet = packet;
        }

        TelemetryPacket packet() {
            // each policy needs fresh packet state
            return new TelemetryPacket(
                    packet.getPacketId(),
                    sourceId,
                    packet.getCreatedAt(),
                    packet.getTemperatureC(),
                    packet.getBatteryPercent(),
                    packet.getAltitudeKm(),
                    packet.getPriority(),
                    20,
                    packet.getSizeKb(),
                    packet.getDeadlineStep()
            );
        }
    }
}
