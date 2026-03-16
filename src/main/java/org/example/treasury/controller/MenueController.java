package org.example.treasury.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * MenueController is a Spring MVC controller that handles requests related to the menu.
 * It provides endpoints for rendering different menu views.
 */

@Controller
@RequestMapping("/api/menue")

public class MenueController {
  /**
   * getindex is a GET endpoint that returns the index view.
   *
   * @return the name of the index view
   */
  @GetMapping("/index")
  public String getindex() {
    return "index";
  }

  /**
   * getShoeMenue is a GET endpoint that returns the shoe menu view.
   *
   * @return the name of the shoe menu view
   */
  @GetMapping("/shoeMenue")
  public String getShoeMenue() {
    return "shoeMenue";
  }

  /**
   * getPreciousMetalMenue is a GET endpoint that returns the precious metal menu view.
   *
   * @return the name of the precious metal menu view
   */
  @GetMapping("/displayMenue")
  public String getDisplayMenue() {
    return "displayMenue";
  }


}