package com.betx.adapter.web;

import com.betx.application.BetxInterfaceActivityItem;
import com.betx.application.BetxInterfaceActivityService;
import com.betx.application.BetxInterfaceStatusService;
import com.betx.application.BetxInterfaceStatusView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interface")
@ConditionalOnProperty(name = "betx.interface.enabled", havingValue = "true")
public class BetxInterfaceController {
    private final BetxInterfaceStatusService statusService;
    private final BetxInterfaceActivityService activityService;

    public BetxInterfaceController(
        BetxInterfaceStatusService statusService,
        BetxInterfaceActivityService activityService
    ) {
        this.statusService = statusService;
        this.activityService = activityService;
    }

    @GetMapping("/status")
    public BetxInterfaceStatusView status() {
        return statusService.status();
    }

    @GetMapping("/activity")
    public List<BetxInterfaceActivityItem> activity() {
        return activityService.recent();
    }
}
