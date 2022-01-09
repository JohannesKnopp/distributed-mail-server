package dslab.entity;

import java.util.ArrayList;

public class Message {

    private Integer id;
    private String from;
    private ArrayList<String> to;
    private String subject;
    private String data;
    private String hash;

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

    public String getSubject() {
        return subject;
    }

    public String getData() {
        return data;
    }


}
