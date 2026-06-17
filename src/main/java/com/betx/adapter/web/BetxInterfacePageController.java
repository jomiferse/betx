package com.betx.adapter.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "betx.interface.enabled", havingValue = "true")
public class BetxInterfacePageController {
    @GetMapping("/")
    public String home() {
        return "redirect:/interface/";
    }

    @GetMapping({"/interface", "/interface/"})
    public String interfaceHome() {
        return "forward:/interface/index.html";
    }
}
