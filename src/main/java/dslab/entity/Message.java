package dslab.entity;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class Message {

    private Integer id;
    private String from;
    private ArrayList<String> to;
    private String subject;
    private String data;
    private String hash; //optional

    public Message () {}

    public Message(Integer id, String from, ArrayList<String> to, String subject, String data, String hash) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.data = data;
        this.hash = hash;
    }
    public Message(Integer id, String from, ArrayList<String> to, String subject, String data) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.data = data;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public String getFrom() {
        return from;
    }

    public ArrayList<String> getTo() {
        return to;
    }

    public String getAllTos() {
        return String.join(",", to);
    }

    public String getSubject() {
        return subject;
    }

    public String getData() {
        return data;
    }

    public String getHash() {
        return hash;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(ArrayList<String> to) {
        this.to = to;
    }

    public void addTo(String to) {
        this.to.add(to);
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
