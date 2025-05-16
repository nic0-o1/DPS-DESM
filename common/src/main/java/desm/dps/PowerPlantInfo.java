package desm.dps;

public class PowerPlantInfo {
    private String plantId;
    private String address;
    private int port;

    public PowerPlantInfo() {}
    public PowerPlantInfo(String plantId, String address, int port) {
        this.plantId = plantId;
        this.address = address;
        this.port = port;
    }

    public String getPlantId() {
        return plantId;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "PowerPlantInfo{" +
                "plantId='" + plantId + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PowerPlantInfo that = (PowerPlantInfo) o;
        return port == that.port &&
                java.util.Objects.equals(plantId, that.plantId) &&
                java.util.Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(plantId, address, port);
    }
}
