package com.xuhaoran.chapter05;

public class Event {
    public String user;
    public String url;
    public Long timestamp;

    public Event(){

    }
    public Event(String user, String url, Long timestamp){
        this.user = user;
        this.url = url;
        this.timestamp = timestamp;
    }


    @Override
    public String toString() {
        return "Event{" +
                "user='" + user + '\'' +
                ", url='" + url + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
