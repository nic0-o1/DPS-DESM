package desm.dps;

public class EnergyRequest {

    private String requestID;
    private int amountKWh;
    private Long timestamp;

    public EnergyRequest() {}
    public EnergyRequest(String requestID, int amountKWh, Long timestamp) {
        this.requestID = requestID;
        this.amountKWh = amountKWh;
        this.timestamp = timestamp;
    }

    public String getRequestID() { return requestID; }
    public int getAmountKWh() { return amountKWh; }
    public Long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "EnergyRequest{" +
                "requestID='" + requestID + '\'' +
                ", amountKWh=" + amountKWh +
                ", timestamp=" + timestamp +
                '}';
    }
}
