package com.yangstagram.sim;

import com.yangstagram.sim.ActivitySimulator.Config;
import com.yangstagram.sim.ActivitySimulator.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lab 활동 시뮬레이터 (L05) */
@RestController
@RequestMapping("/api/lab/sim")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class SimulatorController {

    private final ActivitySimulator simulator;

    public SimulatorController(ActivitySimulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping
    public Status status() {
        return simulator.status();
    }

    @PutMapping
    public Status configure(@RequestBody Config config) {
        return simulator.configure(config);
    }

    @PostMapping("/step")
    public Status step(@RequestParam(defaultValue = "5") int n) {
        return simulator.step(n);
    }
}
