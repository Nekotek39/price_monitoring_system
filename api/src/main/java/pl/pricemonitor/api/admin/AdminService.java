package pl.pricemonitor.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pricemonitor.persistence.entity.FetchJob;
import pl.pricemonitor.persistence.entity.JobStatus;
import pl.pricemonitor.persistence.entity.Shop;
import pl.pricemonitor.persistence.repository.FetchJobRepository;
import pl.pricemonitor.persistence.repository.ShopRepository;

import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {

    private final FetchJobRepository jobs;
    private final ShopRepository shops;

    public AdminService(FetchJobRepository jobs, ShopRepository shops) {
        this.jobs = jobs;
        this.shops = shops;
    }

    @Transactional(readOnly = true)
    public List<JobView> jobs(JobStatus status) {
        List<FetchJob> list = status == null ? jobs.findTop100ByOrderByCreatedAtDesc()
                : jobs.findTop100ByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(j -> new JobView(j.getId(), j.getShop().getId(),
                j.getProduct() == null ? null : j.getProduct().getId(), j.getType().name(), j.getPriority(),
                j.getStatus().name(), j.getAttempts(), j.getError(), j.getCreatedAt(), j.getStartedAt(),
                j.getFinishedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<ShopView> shops() {
        return shops.findAll().stream().sorted(Comparator.comparing(Shop::getId))
                .map(s -> new ShopView(s.getId(), s.getName(), s.getType().name(),
                        s.getFeedFormat() == null ? null : s.getFeedFormat().name(), s.getFeedUrl(), s.getStatus().name(),
                        s.getUnavailableUntil()))
                .toList();
    }
}
