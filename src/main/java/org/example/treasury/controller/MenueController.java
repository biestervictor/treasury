package org.example.treasury.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/menue")
public class MenueController {
    @GetMapping("/index")
    public String getindex() {


        return "index";
    }

    @GetMapping("/shoeMenue")
    public String getShoeMenue() {
        return "shoeMenue";
    }

    @GetMapping("/displayMenue")
    public String getDisplayMenue() {
        return "displayMenue";
    }


}