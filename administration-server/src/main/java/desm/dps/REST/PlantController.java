package desm.dps.REST;

import desm.dps.PlantRepository;
import desm.dps.PowerPlantInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/plants")
public class PlantController {
    private final PlantRepository plantRepository;

    @Autowired
    public PlantController(PlantRepository plantRepository) {
        this.plantRepository = plantRepository;
    }

    @GetMapping
    public ResponseEntity<List<PowerPlantInfo>> getPlantList() {
        return ResponseEntity.ok(plantRepository.getAllPlants());}

    @PostMapping
    public ResponseEntity<String> addPlant(@RequestBody PowerPlantInfo toAdd) {

        if (plantRepository.registerPlant(toAdd))
            return new ResponseEntity<>("Plant registered!", HttpStatus.ACCEPTED);

        else
            return new ResponseEntity<>("Plant already registered", HttpStatus.BAD_REQUEST);

    }

    @GetMapping("/get/{id}")
    public ResponseEntity<PowerPlantInfo> getPlantById(@PathVariable String id) {
        PowerPlantInfo plant = plantRepository.getPlantById(id);
        if (plant == null)
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok(plant);
    }


}
