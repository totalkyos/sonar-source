package javax.annotation;

import java.io.FileInputStream;
import java.io.InputStream;


@interface Nonnull {}

public class CrossProcedural {
  
  public int identifier(Object p) {
    return p.hashCode();
  }
  
  public int identifier(Object p1, Object p2, Object p3) {
    if (p1 == p2) {
      return p3.hashCode();
    }
    return p1.hashCode() + p2.hashCode();
  }
  
  public int delegate(Object p1, Object p2, Object p3) {
    if (p1 == p2) {
      return identifier(p3);
    }
    return identifier(p1) + identifier(p2);
  }
  
  public void callerNull() {
    int i = identifier(null);  // Noncompliant {{Parameter 1 of method 'identifier' may be null and shall cause a NullPointerException at line 12 of that method}}
    Object a = new String("a");
    i = identifier(a, a, null);  // Not inspected because above sink
  }
  
  public void callerNullCondition() {
    Object a = new String("a");
    int i = identifier(a, a, null);  // Noncompliant {{Parameter 3 of method 'identifier' may be null and shall cause a NullPointerException at line 17 of that method}}
    i = identifier(null);  // Not inspected because above sink
  }
  
  public void callerNullDelegate() {
    int i = delegate(null, null, null);  // Noncompliant {{Parameter 1 of method 'delegate' may be null and shall cause a NullPointerException at line 12 of method 'identifier'}}
  }
  
  public void callerNullDelegateCondition() {
    Object a = new String("a");
    int i = delegate(a, a, null);  // Noncompliant {{Parameter 1 of method 'delegate' may be null and shall cause a NullPointerException at line 12 of method 'identifier'}}
  }
}
