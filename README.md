# DESM (Distributed Energy Supply Management)


**Distributed and Pervasive Systems Project  A.A 2024 / 2025 – University of Milan (Master’s in Computer Science)**  


## ⚙️ Components

### 🌞 Renewable Energy Provider
- Publishes an energy request every 10 seconds (5,000–15,000 kWh).
- Uses MQTT to notify all thermal plants.

### 🔥 Thermal Power Plants
- Compete using a **ring-based election** (Chang & Roberts algorithm).
- Fulfill energy requests and simulate CO₂ emissions.
- Use **gRPC** for plant-to-plant coordination.
- Send CO₂ averages via MQTT using a **sliding window buffer** (8 measurements, 50% overlap).

### 🧠 Administration Server
- Exposes REST API to:
    - Register new plants
    - Return list of active plants
    - Compute pollution statistics between timestamps
- Subscribes to MQTT emissions data.

### 🖥️ Administration Client
- Simple CLI to query server for:
    - Active thermal plants
    - CO₂ emission averages between time ranges



## 🔧 Technologies Used

- **Java**
- **gRPC** – for inter-plant coordination
- **MQTT (Mosquitto)** – for messaging between provider, plants, and server
- **REST API** – for server-client communication
- **Custom concurrency primitives** – threads, buffers, synchronization logic


## 🧪 Features

- Dynamic plant registration
- Sliding window CO₂ measurement buffer
- Real-time pollution monitoring
- Distributed coordination and fairness in request fulfillment



## ⚖️ License

This project is licensed under the **MIT License**. 