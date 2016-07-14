package org.sonar.java.checks;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.util.Optional;

public class A implements Serializable {
  
  private String attr1;
  
  private Optional<String> attr2;  // Noncompliant [[sc=28;ec=33]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
  
  private Optional<String> attr3;  // Compliant: this value-based field is not serialized
  
  private HijrahDate attr4;        // Noncompliant [[sc=22;ec=27]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
  
  private Clock attr5;             // Compliant: Clock is not a value-based class
  
  private LocalDateTime attr6;     // Noncompliant [[sc=25;ec=30]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
  
  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    
  }
  
  public void serialize() throws IOException {
    ObjectOutput out = new ObjectOutputStream(new FileOutputStream("data/person.bin"));
//    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("data/person.bin"));
    out.writeObject(attr1);
    out.writeObject(attr2);
    out.writeObject(attr4);
    out.writeObject(attr5);
    out.writeObject(attr6);
 }
 
}

class B implements Serializable {
  
  private String attr1;
  
  private Optional<String> attr2;   // Compliant: the attribute is not marked as transient, but it is not serialized
  
  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    Object obj = attr2;
    out.writeObject(attr1);
  }
 
}
