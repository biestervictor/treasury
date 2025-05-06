package org.example.treasury.controller;

import org.example.treasury.model.Display;
import org.example.treasury.service.DisplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/displays")
public class DisplayController {

    @Autowired
    private DisplayService displayService;


    @GetMapping
    public List<Display> getAllDisplays() {
        return displayService.getAllDisplays();
    }

    @GetMapping("/{id}")
    public Display getDisplayById(@PathVariable String id) {
        return displayService.getDisplayById(id);
    }

    @PostMapping
    public Display createDisplay(@RequestBody Display display) {
        return displayService.saveDisplay(display);
    }

    @PutMapping("/{id}")
    public Display updateDisplay(@PathVariable String id, @RequestBody Display display) {
        display.set_id(id);
        return displayService.saveDisplay(display);
    }

    @DeleteMapping("/{id}")
    public void deleteDisplay(@PathVariable String id) {
        displayService.deleteDisplay(id);
    }

    @GetMapping("/setCode/{setCode}")
    public List<Display> getDisplaysBySetCode(@PathVariable String setCode) {
        return displayService.findBySetCodeIgnoreCase(setCode);
    }

    @GetMapping("/type/{type}")
    public List<Display> getDisplaysByType(@PathVariable String type) {
        return displayService.getDisplaysByType(type);
    }

    @GetMapping("/valueRange")
    public List<Display> getDisplaysByValueRange(@RequestParam double minValue, @RequestParam double maxValue) {
        return displayService.getDisplaysByValueRange(minValue, maxValue);
    }


    @GetMapping("/list")
    public String getList(@RequestParam(value = "setCode", required = false) String setCode, Model model) {

        displayService.saveDisplay();
        List<Display> displays;
        if (setCode != null && !setCode.isEmpty()) {
            displays = displayService.findBySetCodeIgnoreCase(setCode);
        } else {
            displays = displayService.getAllDisplays();
        }
        //Comment
        model.addAttribute("displays", displays);
        return "displays";
    }

}