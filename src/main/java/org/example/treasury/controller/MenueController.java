package org.example.treasury.controller;

import org.example.treasury.model.Display;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/api/menue")
public class MenueController {
    @GetMapping("/index")
    public String getindex( Model model) {


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
