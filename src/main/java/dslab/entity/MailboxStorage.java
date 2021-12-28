package dslab.entity;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MailboxStorage {

    private AtomicInteger currentId;
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>> messages;

    public MailboxStorage() {
        currentId = new AtomicInteger(0);
        messages = new ConcurrentHashMap<>();
    }

    public void writeMessage(String recipient, Message message) {
        String key = recipient.split("@")[0];
        message.setId(currentId.getAndIncrement());
        messages.computeIfAbsent(key, v -> new ConcurrentLinkedQueue<>());
        messages.get(key).add(message);
    }

    public Iterator<Message> iteratorForKey(String key) {
        if (messages.get(key) != null) {
            return messages.get(key).iterator();
        }
        return null;
    }

    public Message showMessage(String username, Integer id) {
        Iterator<Message> it = iteratorForKey(username);
        if (it != null) {
            Message m;
            while (it.hasNext()) {
                m = it.next();
                if (m.getId().equals(id)) {
                    return m;
                }
            }
        }
        return null;
    }

    public boolean deleteMessage(String username, Integer id) {
        if (messages.containsKey(username)) {
            return messages.get(username).removeIf(v -> v.getId().equals(id));
        }
        return false;
    }

}
