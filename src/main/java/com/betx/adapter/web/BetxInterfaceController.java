package com.betx.adapter.web;

import com.betx.application.BetxInterfaceActivityItem;
import com.betx.application.BetxInterfaceActivityService;
import com.betx.application.BetxInterfaceProperties;
import com.betx.application.BetxInterfaceRuntimeService;
import com.betx.application.BetxInterfaceStatusService;
import com.betx.application.BetxInterfaceStatusView;
import com.betx.domain.config.ConfigPath;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interface")
@ConditionalOnProperty(name = "betx.interface.enabled", havingValue = "true")
public class BetxInterfaceController {
    private final BetxInterfaceStatusService statusService;
    private final BetxInterfaceRuntimeService runtimeService;
    private final BetxInterfaceActivityService activityService;
    private final BetxInterfaceProperties properties;

    public BetxInterfaceController(
        BetxInterfaceStatusService statusService,
        BetxInterfaceRuntimeService runtimeService,
        BetxInterfaceActivityService activityService,
        BetxInterfaceProperties properties
    ) {
        this.statusService = statusService;
        this.runtimeService = runtimeService;
        this.activityService = activityService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public BetxInterfaceStatusView status() {
        return statusService.status();
    }

    @PostMapping("/activate")
    public BetxInterfaceStatusView activate() {
        runtimeService.activate(new ConfigPath(properties.configPath()));
        return statusService.status();
    }

    @PostMapping("/pause")
    public BetxInterfaceStatusView pause() {
        runtimeService.pause();
        return statusService.status();
    }

    @GetMapping("/activity")
    public List<BetxInterfaceActivityItem> activity() {
        return activityService.recent();
    }
}
