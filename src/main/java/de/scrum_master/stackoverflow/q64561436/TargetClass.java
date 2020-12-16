package de.scrum_master.stackoverflow.q64561436;

import java.util.Random;

public class TargetClass {
  public String greet(String recipient) {
    if (new Random().nextBoolean()) {
      String prefix = "Hello ";
      String postfix = "!";
      return prefix + recipient + postfix;
    }
    else {
      String greeting = "Lazy to greet today...";
      return greeting;
    }
  }
}
