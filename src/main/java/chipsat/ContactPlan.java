package chipsat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactPlan {
    private final List<ContactWindow> contacts = new ArrayList<>();

    public void addContact(ContactWindow contact) {
        contacts.add(contact);
    }

    public List<ContactWindow> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    public List<ContactWindow> contactsFrom(int nodeId) {
        List<ContactWindow> result = new ArrayList<>();

        for (ContactWindow contact : contacts) {
            if (contact.involves(nodeId)) {
                result.add(contact);
            }
        }

        return result;
    }

    public List<ContactWindow> activeAt(int step) {
        List<ContactWindow> result = new ArrayList<>();

        for (ContactWindow contact : contacts) {
            if (contact.getStartStep() <= step && step <= contact.getEndStep()) {
                result.add(contact);
            }
        }

        return result;
    }
}
