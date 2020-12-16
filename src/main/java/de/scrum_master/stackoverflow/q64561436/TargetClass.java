package de.scrum_master.stackoverflow.q64561436;

import java.util.Random;

public class TargetClass {
  public String greet_JavassistWorks1(String recipient) {
    return "Hello " + recipient + "!";
  }

  public String greet_JavassistWorks2(String recipient) {
    String prefix = "Hello ";
    String postfix = "!";
    return prefix + recipient + postfix;
  }

  public String greet_JavassistWorks3(String recipient) {
    if (new Random().nextBoolean()) {
      String prefix = "Hello ";
      String postfix = "!";
      return prefix + recipient + postfix;
    }
    else {
      return "Lazy to greet today...";
    }
  }

  public String greet_JavassistFails(String recipient) {
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
