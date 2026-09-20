# ChipSat Contact-Aware Telemetry Network

A Java simulation of **delay-tolerant telemetry routing and scheduling for small-satellite swarms** with intermittent, predictable, and capacity-limited communication links.

Unlike a traditional shortest-path network, a ChipSat may generate telemetry when no complete route to Earth currently exists. The system therefore models communication as a **time-dependent routing and resource scheduling problem**: packets can be stored onboard, forwarded through future satellite contacts, and scheduled according to mission priority, deadlines, available bandwidth, and modeled energy cost.

The project contains two networking layers. `TelemetryNetwork` models the communication graph available at the current simulation step and supports algorithms such as BFS, Dijkstra, packet loss, node failures, movement, and store-and-forward routing. `ContactAwareRouter` operates over a known future contact plan and searches for feasible sequences of contacts that satisfy packet-size, timing, remaining-capacity, and deadline constraints.

`MissionScheduler` coordinates competing telemetry by prioritizing packets according to **criticality, deadline, and packet size**. When a packet is assigned a route, bandwidth is reserved on every contact along that route, meaning earlier scheduling decisions directly affect the routes available to later packets. This allows scarce early downlink opportunities to be preserved for urgent health telemetry while larger science payloads can wait for later high-capacity contacts when their deadlines allow it.

The simulator also includes a policy benchmark that evaluates three routing strategies on the **same 180-packet workload and finite contact plan**:

* **Immediate Route** — transmits only when a complete end-to-end path exists at packet generation time.
* **Energy-Aware Now** — also requires a current route, but selects paths using modeled radio-energy cost.
* **Mission Scheduler** — may wait for future contacts, reserve capacity, and coordinate transmissions around deadlines and mission priority.

The benchmark compares how different routing policies use limited contact opportunities rather than simply measuring shortest paths.

The project is inspired by **delay/disruption-tolerant satellite networking**, but it is intentionally a software simulation rather than a full implementation of Bundle Protocol, NASA ION, or orbital propagation. Its focus is on **contact-aware routing, capacity reservation, telemetry prioritization, deadlines, energy-aware decisions, fault handling, and network visualization**.
