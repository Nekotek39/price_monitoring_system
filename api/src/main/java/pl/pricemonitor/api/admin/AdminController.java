package pl.pricemonitor.api.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pricemonitor.api.jobs.FetchJobScheduler;
import pl.pricemonitor.persistence.entity.JobStatus;
import pl.pricemonitor.persistence.service.SystemSettings;

import java.util.List;
import java.util.Map;

/** Administrator panel: settings (popularity threshold, data age, indexing mode), jobs and shops. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SystemSettings settings;
    private final FetchJobScheduler scheduler;
    private final AdminService service;

    public AdminController(SystemSettings settings, FetchJobScheduler scheduler, AdminService service) {
        this.settings = settings;
        this.scheduler = scheduler;
        this.service = service;
    }

    @GetMapping("/settings")
    public Map<String, String> settings() {
        return settings.all();
    }

    /** Example body: {"popularity.threshold": "20", "indexing.mode": "CYCLIC_ONLY"} */
    @PutMapping("/settings")
    public Map<String, String> updateSettings(@RequestBody Map<String, String> changes) {
        return settings.update(changes);
    }

    @PostMapping("/import")
    public Map<String, Integer> importAll() {
        return Map.of("scheduledJobs", scheduler.scheduleImport());
    }

    @GetMapping("/jobs")
    public List<JobView> jobs(@RequestParam(required = false) JobStatus status) {
        return service.jobs(status);
    }

    @GetMapping("/shops")
    public List<ShopView> shops() {
        return service.shops();
    }
}
