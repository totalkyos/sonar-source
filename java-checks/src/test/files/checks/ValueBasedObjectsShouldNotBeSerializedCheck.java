package org.sonar.java.checks;

import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.util.Optional;

public class A implements Serializable {
}

public class AA extends A /*implements Serializable */{
  
//  private String attr1;
//  
//  private Optional<String> attr2;  // Noncompliant [[sc=28;ec=33]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
//  
//  private HijrahDate attr3;        // Noncompliant [[sc=22;ec=27]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
//  
//  private Clock attr4;             // Compliant, as Clock is not a value-based class
//  
  private LocalDateTime attr5;     // Noncompliant [[sc=25;ec=30]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
  
  private transient Optional<String> attr6;
  
  private transient Clock attr7; 
  
//  public void doSomething() {
//    Serializable obj = new Serializable() {
//      private String attr1;
//      private Optional<String> attr2;  // Noncompliant [[sc=32;ec=37]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
//    };
//  }
 
}

class NonSerializableClass {
  
  private String attr1;
  
  private Optional<String> attr2;   // Compliant, as this class is not serializable
  
  private LocalDateTime attr3;      // Compliant, as this class is not serializable
 
}
