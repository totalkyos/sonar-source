package org.sonar.java.checks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
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
  
  private Optional<String> attr3;  // Compliant, as this value-based field is not serialized
  
  private HijrahDate attr4;        // Noncompliant [[sc=22;ec=27]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
  
  private Clock attr5;             // Compliant, as Clock is not a value-based class
  
  private LocalDateTime attr6;     // Noncompliant [[sc=25;ec=30]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
  
  public void serialize() throws IOException {
    ObjectOutput out = new ObjectOutputStream(new FileOutputStream("fileName"));
//    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("fileName"));
    out.writeObject(attr1);
    out.writeObject(attr2);        // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
    out.writeObject(attr4);        // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
    out.writeObject(attr5);
    out.writeObject(attr6);        // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
    
    String var1 = "hello";
    Optional<String> var2 = Optional.of("hello");
    out.writeObject(var1);
    out.writeObject(var2);         // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
  }
  
  public void deserialize() throws Exception {
    ObjectInput in = new ObjectInputStream(new FileInputStream("fileName"));
    String           obj1 = (String)          in.readObject();
    Optional<String> obj2 = (Optional<String>)in.readObject();  // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
    HijrahDate       obj4 = (HijrahDate)      in.readObject();  // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
    Clock            obj5 = (Clock)           in.readObject();
    LocalDateTime    obj6 = (LocalDateTime)   in.readObject();  // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
  }
  
  public void doSomething() {
    Serializable obj = new Serializable() {
      private String attr1;
      private Optional<String> attr2;  // Noncompliant [[sc=32;ec=37]] {{Make this value-based field transient so it is not included in the serialization of this class.}}
    };
  }
 
}

class NonSerializableClass {
  
  private String attr1;
  
  private Optional<String> attr2;   // Compliant, as this class is not serializable
  
  private LocalDateTime attr3;      // Compliant, as this class is not serializable
 
  public void serialize() throws IOException {
    ObjectOutput out = new ObjectOutputStream(new FileOutputStream("fileName"));
    out.writeObject(attr1);
    out.writeObject(attr2);  // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
    
    String var1 = "hello";
    Optional<String> var2 = Optional.of("hello");
    out.writeObject(var1);
    out.writeObject(var2);   // Noncompliant [[sc=5;ec=26]] {{Remove the [de]serialization of this value-based field or variable.}}
  }

}
