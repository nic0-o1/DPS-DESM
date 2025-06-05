package desm.dps.REST;

import desm.dps.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsController.class);
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * REST endpoint to get the average CO2 emission levels sent by all plants
     * that occurred (i.e., the list of averages was computed/sent by the plant)
     * between timestamp t1 and timestamp t2.
     *
     * @param t1 Start timestamp (in milliseconds).
     * @param t2 End timestamp (in milliseconds).
     * @return A ResponseEntity containing the average CO2 value or an error message.
     */
    @GetMapping("/co2/average")
    public ResponseEntity<?> getAverageCo2(
            @RequestParam("t1") long t1,
            @RequestParam("t2") long t2) {

        logger.info("Received request for average CO2 emissions between t1={} and t2={}", t1, t2);

        try {
            double average = statisticsService.getAverageCo2BetweenTimestamps(t1, t2);

            if (Double.isNaN(average)) {
                logger.info("No CO2 data found for timestamps t1={}, t2={}", t1, t2);
                return ResponseEntity.status(404)
                        .body(Map.of("error", "No CO2 data found for the specified time range"));
            }
            logger.info("Successfully processed request for average CO2. Result: {}", average);
            return ResponseEntity.ok(average);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for average CO2: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error processing request for average CO2 between t1={}, t2={}", t1, t2, e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "An internal server error occurred"));
        }
    }
}