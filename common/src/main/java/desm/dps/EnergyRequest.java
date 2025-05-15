package desm.dps;

public class EnergyRequest {

    private String requestID;
    private int amountKWh;
    private long timestamp;

    public EnergyRequest() {}
    public EnergyRequest(String requestID, int amountKWh, long timestamp) {
        this.requestID = requestID;
        this.amountKWh = amountKWh;
        this.timestamp = timestamp;
    }

    public String getRequestID() { return requestID; }
    public int getAmountKWh() { return amountKWh; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "EnergyRequest{" +
                "requestID='" + requestID + '\'' +
                ", amountKWh=" + amountKWh +
                ", timestamp=" + timestamp +
                '}';
    }
}
