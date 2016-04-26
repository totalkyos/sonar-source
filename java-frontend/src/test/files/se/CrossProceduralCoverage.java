package javax.annotation;

import org.apache.commons.lang.NullArgumentException;

@interface Nonnull {}
@interface Nullable {}

public abstract class CrossProcedural {
  
  private boolean aNull;
  
  private CrossProcedural() {
    Object a = getObject("a");
    if (compute(a) > 100) {
      return;
    }
    check(a);
  }

  public void caller(Integer i, Integer j) {
    Object a = "test";
    nullableMethod(a);
    compute(a);
    if (greaterThan(i, j)) {
      log("Greater");
    }
  }

  @NonNull
  public boolean complexYield() {
    Object a = getObject("a");
    check(a);
    int i = 0;
    int j = 0;
    int k = 0;
    if(compute(a) > 100) {
      i = 10;
    }
    if (compute(a) < 50) {
      j = 30;
    }
    if (i < j) {
      k = 40;
    }
    if (k < j) {
      i = 10;
    }
    return a.isString();
  }
  
  @Nonnull
  private Object getObject(String a) {
    return new String(a);
  }
  
  private int compute(Object b){
    return b.hashCode();
  }
  
  private void check(Object a) {
    if (compute(a)) {
      a = getObject("b");
    }
    return;
  }
  
  private boolean greaterThan(Integer a, Integer b) {
    return a > b;
  }
  
  private boolean isString(Object a) {
    return a instanceof String;
  }
}
