package com.xuhaoran.chapter12;

public class OrderEvent {
    public String userId;
    public String orderId;
    public String evnetType;
    public Long timestamp;

    public OrderEvent(String userId, String orderId, String evnetType, Long timestamp) {
        this.userId = userId;
        this.orderId = orderId;
        this.evnetType = evnetType;
        this.timestamp = timestamp;
    }

    public OrderEvent() {
    }

    @Override
    public String toString() {
        return "OrderEvent{" +
                "userId='" + userId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", evnetType='" + evnetType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
