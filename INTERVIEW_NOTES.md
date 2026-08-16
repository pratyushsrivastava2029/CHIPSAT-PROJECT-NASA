# Interview walkthrough

## 30-second version

I built a Java simulation of a telemetry mesh for a swarm of ChipSats. Each satellite is a node in a dynamic graph, and communication links are edges that exist only when two nodes are within range. Satellites generate telemetry and route it through neighboring spacecraft to a ground station. I implemented both BFS for minimum-hop routing and Dijkstra for cost-aware routing, then added failures, packet loss, topology rebuilding, rerouting, and delivery metrics.

## Why I built it

I wanted to model the software side of a problem I had already seen in satellite networking: small spacecraft may not all have direct ground contact, so they need to cooperate as a relay network.

## Main data structures

- `HashMap<Integer, ChipSat>` for node lookup
- `HashMap<Integer, List<Link>>` for the adjacency list
- `ArrayDeque<Integer>` for BFS
- `PriorityQueue` for Dijkstra
- `HashMap<Integer, Integer>` as a parent map for reconstructing routes
- `HashSet<Integer>` for BFS visited state

## BFS explanation

Start with the source satellite in a queue.

Each time I remove a node, I add any unvisited neighbors.

Because BFS explores one graph depth at a time, the first time I reach the ground station I know that route uses the minimum number of edges.

I store each node's parent so I can reconstruct the route backward from ground to source.

## Dijkstra explanation

BFS treats all links as equal.

For Dijkstra, each link gets a cost based on distance plus a battery penalty. A priority queue always expands the currently cheapest known route.

That lets the network avoid a weak relay even when it is technically reachable.

## Failure behavior

When a satellite goes offline, I rebuild the adjacency graph.

That removes all edges connected to the failed spacecraft.

A later route computation therefore naturally avoids the failed node.

## Complexity

Graph rebuild: O(V^2), because the simulator checks every satellite pair.

BFS: O(V + E).

Dijkstra: O((V + E) log V).

For a small swarm, O(V^2) topology construction is fine. At larger scale I would spatially partition the nodes so I don't test every pair.

## Tradeoff I would mention

The simulator recalculates topology globally after movement or failure. That is intentionally simple and reliable for this scale.

In a larger distributed network, I would use incremental link-state updates or local neighbor discovery instead.

## Strong next-step answer

The next feature I would add is store-and-forward behavior. Right now a packet either has a route or it does not. A real delay-tolerant satellite network may need to queue the packet locally until a useful contact becomes available, then forward it later.


## Visual demo explanation

The visualization is intentionally a view over the same backend graph rather than a separate fake animation.

Every visible edge comes from the `TelemetryNetwork` adjacency list. When a satellite fails or moves, the backend rebuilds the graph and the view redraws it. When I send telemetry, the route shown on screen is the actual route returned by BFS or Dijkstra.

That separation matters because the UI is not deciding how networking works. `TelemetryNetwork` owns routing and topology; `NetworkVisualizer` only presents and controls it.

A strong interview sentence:

"I kept the routing model separate from the visualization so the UI is just observing and driving the network state. That made it much easier to test the graph algorithms independently."

### UI architecture

- Swing `JFrame` = application window
- custom `JPanel` = graph renderer
- Swing `Timer` = packet-hop animation and auto demo
- control panel = routing selection, source selection, failures, recovery, orbit step
- metrics panel = backend delivery statistics
- event feed = user-readable trace of routing behavior

### If asked why Swing

"I wanted the simulator to be clone-and-run with a normal JDK and no build tool or front-end dependencies, because the interesting part of the project is the networking model rather than setup."
