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

/**
 * REST Controller for providing statistical data about the power plant network.
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsController.class);
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Handles GET requests to calculate the average CO2 emissions across all plants within a given time range.
     * The time range is based on when the pollution data was computed and sent by the plants.
     *
     * @param t1 The start of the time range (as a Unix timestamp in milliseconds).
     * @param t2 The end of the time range (as a Unix timestamp in milliseconds).
     * @return A ResponseEntity containing the calculated average, or an appropriate error response.
     */
    @GetMapping("/co2/average")
    public ResponseEntity<?> getAverageCo2(
            @RequestParam("t1") long t1,
            @RequestParam("t2") long t2) {

        logger.info("Received request for average CO2 emissions between t1={} and t2={}", t1, t2);
        try {
            double average = statisticsService.getAverageCo2BetweenTimestamps(t1, t2);

            if (Double.isNaN(average)) {
                logger.warn("No CO2 data found for the time range [{}, {}]", t1, t2);
                return ResponseEntity.status(404)
                        .body(Map.of("error", "No CO2 data found for the specified time range."));
            }

            logger.info("Successfully calculated average CO2 for range [{}, {}]: {}", t1, t2, average);
            return ResponseEntity.ok(average);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for average CO2 with range [{}, {}]: {}", t1, t2, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("An internal error occurred while processing request for average CO2 for range [{}, {}]", t1, t2, e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "An internal server error occurred."));
        }
    }
}