package chipsat;

import java.util.Collections;
import java.util.List;

public class DeliveryResult {
    private final TelemetryPacket packet;
    private final boolean delivered;
    private final boolean rerouted;
    private final String message;
    private final List<Integer> route;

    private DeliveryResult(TelemetryPacket packet,
                           boolean delivered,
                           boolean rerouted,
                           String message,
                           List<Integer> route) {
        this.packet = packet;
        this.delivered = delivered;
        this.rerouted = rerouted;
        this.message = message;
        this.route = route;
    }

    public static DeliveryResult delivered(TelemetryPacket packet, List<Integer> route) {
        return new DeliveryResult(
                packet,
                true,
                false,
                "Delivered via " + TelemetryNetwork.formatRoute(route),
                route
        );
    }

    public static DeliveryResult rerouted(TelemetryPacket packet,
                                          List<Integer> originalRoute,
                                          List<Integer> newRoute) {
        return new DeliveryResult(
                packet,
                true,
                true,
                "Rerouted from [" + TelemetryNetwork.formatRoute(originalRoute)
                        + "] to [" + TelemetryNetwork.formatRoute(newRoute) + "]",
                newRoute
        );
    }

    public static DeliveryResult failed(TelemetryPacket packet, String reason) {
        return new DeliveryResult(packet, false, false, reason, Collections.emptyList());
    }

    public boolean isDelivered() {
        return delivered;
    }

    public boolean isRerouted() {
        return rerouted;
    }

    public String getMessage() {
        return message;
    }

    public List<Integer> getRoute() {
        return route;
    }

    @Override
    public String toString() {
        String status = delivered ? "DELIVERED" : "DROPPED";
        return status + " | " + packet + " | " + message;
    }
}
