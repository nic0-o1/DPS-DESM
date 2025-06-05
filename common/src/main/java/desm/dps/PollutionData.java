package desm.dps;

import java.util.List;

public class PollutionData {
    private String plantId;
    private Long listComputationTimestamp; // Timestamp when the list of averages was computed by the plant [cite: 14, 90]
    private List<Double> averages;

    // Jackson typically requires a no-arg constructor for deserialization
    public PollutionData() {
    }

    public PollutionData(String plantId, Long listComputationTimestamp, List<Double> averages) {
        this.plantId = plantId;
        this.listComputationTimestamp = listComputationTimestamp;
        this.averages = averages;
    }

    public String getPlantId() {
        return plantId;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public Long getListComputationTimestamp() {
        return listComputationTimestamp;
    }

    public void setListComputationTimestamp(Long listComputationTimestamp) {
        this.listComputationTimestamp = listComputationTimestamp;
    }

    public List<Double> getAverages() {
        return averages;
    }

    public void setAverages(List<Double> averages) {
        this.averages = averages;
    }

    @Override
    public String toString() {
        return "PollutionData{" +
                "plantId='" + plantId + '\'' +
                ", listComputationTimestamp=" + listComputationTimestamp +
                ", averages=" + averages +
                '}';
    }
}
