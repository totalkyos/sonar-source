package org.sonar.java.checks.targets;

import java.util.stream.IntStream;

public class UnusedPrivateMethod {

  private UnusedPrivateMethod() {}
  private UnusedPrivateMethod(int a) {} // Noncompliant

  public UnusedPrivateMethod(String s) {
    init();
  }

  private void init() {
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    // this method should not be considered as dead code, see Serializable contract
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    // this method should not be considered as dead code, see Serializable contract
  }

  private Object writeReplace() throws java.io.ObjectStreamException {
    // this method should not be considered as dead code, see Serializable contract
    return null;
  }

  private Object readResolve() throws java.io.ObjectStreamException {
    // this method should not be considered as dead code, see Serializable contract
    return null;
  }

  private void readObjectNoData() throws java.io.ObjectStreamException {
    // this method should not be considered as dead code, see Serializable contract
  }

  @SuppressWarnings("unused")
  private int unusedPrivateMethod() {
    return 1;
  }
  private int unusedPrivateMethod(int a, String s) { // Noncompliant {{Remove this unused private "unusedPrivateMethod" method.}}
    return 1;
  }

  private void varargs(String first, String second, Object... objects) {
  }

  private void varargs(String... strings) {
  }

  public void usage() {
    varargs("", "", new Object());
    varargs("", "", ""); // should resolve to 'String...' and not 'String, Object...'
  }

  public enum Attribute {
    ID("plop", "foo", true);

    Attribute(String prettyName, String type, boolean hidden) { }

    private Attribute(String name) { } // Noncompliant

    Attribute(String prettyName, String[][] martrix, int i) { // Noncompliant {{Remove this unused private "Attribute" constructor.}}
    }

  }

  private class A {
    A(int a) {}
    private A(){}
    private <T> T foo(T t) {
      return null;
    }

    public void bar() {
      foo("");
    }
  }

}

class OuterClass {

  private static <T> void genericMethod(T argument) {
    new Object() {
      private void unused() { // Noncompliant {{Remove this unused private "unused" method.}}
      }
    };
  }

  private static <T extends java.util.List<String>> void complexGenericMethod(T argument) {
  }

  class NestedGenericClass<T> {
    private NestedGenericClass(T argument) { // Compliant
    }

    private void genericMethod(T argument) { // Compliant
    }
  }

  class ComplexNestedGenericClass<T extends java.util.Collection<Object>> {
    private ComplexNestedGenericClass(T argument) {
    }

    private void genericMethod(T argument) {
    }
  }

  public void test() {
    genericMethod("string");
    complexGenericMethod(new java.util.ArrayList<String>());
    new NestedGenericClass<java.util.List<Object>>(new java.util.ArrayList<Object>()).genericMethod(new java.util.LinkedList<Object>());
    new ComplexNestedGenericClass<java.util.List<Object>>(new java.util.ArrayList<Object>()).genericMethod(new java.util.LinkedList<Object>());
  }

}

class Lambdas {
  void method(){
    IntStream.range(1, 5)
      .map((x)-> x*x )
      .map(x -> x * x)
      .map((int x) -> x * x)
      .map((x)-> x*x )
    ;
  }

}
