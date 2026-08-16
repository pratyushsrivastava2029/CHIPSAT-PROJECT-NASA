package chipsat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactRoute {
    private final List<ContactWindow> contacts;
    private final int arrivalStep;
    private final double energyCost;

    public ContactRoute(List<ContactWindow> contacts,
                        int arrivalStep,
                        double energyCost) {
        this.contacts = new ArrayList<>(contacts);
        this.arrivalStep = arrivalStep;
        this.energyCost = energyCost;
    }

    public List<ContactWindow> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    public int getArrivalStep() {
        return arrivalStep;
    }

    public double getEnergyCost() {
        return energyCost;
    }

    public String describe(int sourceId) {
        StringBuilder out = new StringBuilder();
        int current = sourceId;
        out.append(label(current));

        for (ContactWindow contact : contacts) {
            int next = contact.otherSide(current);
            out.append(" -> ")
               .append(label(next))
               .append("@")
               .append(Math.max(contact.getStartStep(), 0));
            current = next;
        }

        out.append(" | arrive=").append(arrivalStep);
        return out.toString();
    }

    private String label(int id) {
        return id == GroundStation.ID ? "Ground" : "Sat-" + id;
    }
}
