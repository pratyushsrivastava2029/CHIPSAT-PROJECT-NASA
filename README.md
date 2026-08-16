# ChipSat Contact-Aware Telemetry Network

Java simulation of telemetry routing and scheduling for a small-satellite swarm with intermittent, predictable, capacity-limited contacts.

## Actual problem

The problem is **not** "find the shortest path."

A ChipSat can generate telemetry when no end-to-end route to Earth exists at that moment. A useful relay may only come into contact several steps later. Multiple packets can compete for the same short downlink. Critical health data may have a near deadline while a large science payload can consume most of the available capacity.

So the software has to answer:

> Which telemetry should be sent over which sequence of current or future contacts so the highest-value data reaches Ground before its deadline, without exceeding contact capacity or wasting energy?

That makes this a **time-dependent routing + resource scheduling problem**.

## Two networking layers

`TelemetryNetwork` handles the reactive question:

> What communication graph exists right now?

It still supports BFS, Dijkstra, packet loss, failures, movement, and local store-and-forward.

`ContactAwareRouter` handles the harder question:

> Given a known future contact plan, when should this packet move and which future contacts should it use?

A valid route must satisfy time windows, packet size, remaining capacity, and deadline.

## Mission scheduling

`MissionScheduler` sorts competing telemetry by:

1. criticality
2. deadline
3. packet size

When a route is selected, capacity is reserved on every future contact in that route. Later packets then have to plan around the reduced capacity.

So scheduling packet A changes the feasible routes for packet B.

## Why this is more interesting than shortest path

Example:

```text
Sat-8 has:
- 120 KB CRITICAL battery/thermal telemetry, deadline step 6
- 900 KB SCIENCE payload, deadline step 11

Fast downlink path:
Sat-8 -> Sat-5 -> Sat-3 -> Sat-1 -> Ground
but the final contact only has 650 KB

Slower path:
Sat-8 -> Sat-7 -> Sat-4 -> Sat-2 -> Ground
opens later but has much more capacity
```

The scheduler should reserve the scarce early path for urgent telemetry and push the large science payload onto a later contact sequence if it still meets its deadline.

That is the actual engineering decision this project models.

## Run

```bash
mkdir -p out
javac -d out src/main/java/chipsat/*.java
java -cp out chipsat.MissionDemo
```

Visualizer:

```bash
java -cp out chipsat.NetworkVisualizer
```

Tests:

```bash
javac -d out src/main/java/chipsat/*.java src/test/java/chipsat/*.java
java -cp out chipsat.TelemetryNetworkTest
java -cp out chipsat.ContactAwareRouterTest
```

## Honest scope

This is a software simulation inspired by delay/disruption-tolerant satellite networking. It is not a full Bundle Protocol or NASA ION implementation, and the contact plan is synthetic rather than generated from real orbital propagation.

The focus is on time-dependent routing, capacity reservation, telemetry prioritization, deadlines, energy-aware decisions, fault handling, and visualization.


## Policy benchmark

The project now includes a deterministic A/B/C experiment rather than relying only on a visual demo.

Three policies receive the **same 180-packet workload and the same finite contact plan**:

- `Immediate Route` — packet must have a complete active route at generation time.
- `Energy-Aware Now` — same current-only limitation, but minimizes modeled radio-energy cost.
- `Mission Scheduler` — may wait for future contacts, respects deadlines/capacity, and reserves scarce bandwidth in mission-priority order.

Run the terminal benchmark:

```bash
java -cp out chipsat.BenchmarkRunner
```

Run the benchmark dashboard:

```bash
java -cp out chipsat.BenchmarkVisualizer
```

On Windows you can also double-click:

```text
run-benchmark.bat
```

Important: the benchmark numbers are **simulation results**, not NASA performance claims. The purpose is to make routing policies testable under identical synthetic conditions.
