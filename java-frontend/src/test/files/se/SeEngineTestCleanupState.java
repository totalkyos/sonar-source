class A0 {
  
  private Object x;

  boolean boolMethod() {
    return x == null;
  }

  void test_reduced_steps(Object c) {
    Object a = new Object();
    Object b;
    if (boolMethod()) {
      b = new Object();
    }
    a.toString();
  }
}
