class A {
  void foo(int r) {
    int z1 = 0;
    int z2 = z1;
    r = 1 / z2; // Noncompliant {{Make sure 'z2' can't be zero before doing this division.}}
  }

  void roo(int r) {
    int z1 = 0;
    int z2 = z1;
    r = z2 / 1; // Compliant
  }

  void boo(int r) {
    r = 1 / '\0'; // Noncompliant [[sc=13;ec=17]] {{Make sure this expression can't be zero before doing this division.}}
  }

  void choo(int r) {
    r = 1 / '4'; // Compliant
  }

  void goo(int r) {
    r = 1 / (int) '\u0000'; // Noncompliant [[sc=13;ec=27]] {{Make sure this expression can't be zero before doing this division.}}
  }

  void moo(int r) {
    r = 1 / (int) 0b0101_000_01; // Compliant
  }

  void doo(int r) {
    r = 1 / (int) getDoubleValue(); // Compliant
  }

  double getDoubleValue() {
    return -1;
  }

  void zug(int r) {
    int z1 = 0x0;
    int z2 = 15;
    z2 *= z1;
    r = 1 / z2; // Noncompliant {{Make sure 'z2' can't be zero before doing this division.}}
  }

  void zup(int r) {
    int z1 = 0x1;
    int z2 = 15;
    z2 *= z1;
    r = 1 / z2; // Compliant
  }

  void pug(int x, int y, int a) {
    x *= y;
    if (x == 0) {
      int b = a / x; // Noncompliant
    }
  }

  void rug(int r) {
    int z1 = 0;
    int z2 = 15;
    r = 1 / (z2 * z1); // Noncompliant [[sc=13;ec=22]] {{Make sure this expression can't be zero before doing this division.}}
  }

  void tug(int r) {
    int z1 = 0;
    int z2 = 15;
    r = 1 / (z2 + z1); // Compliant
  }

  void mug(int r) {
    int z1 = 0x00;
    int z2 = 0x00L;
    r = 1 / (z2 + z1); // Noncompliant [[sc=13;ec=22]] {{Make sure this expression can't be zero before doing this division.}}
  }

  int pdf(int p) {
    int r = 0;
    r = -r;
    return p / r; // Noncompliant
  }

  int ptt(int p) {
    int r = 0;
    r++;
    return p / r; // Compliant
  }

  int jac(int p) {
    if (p == 0) {
      return 0;
    }

    if (p < 0) {
      p = -p;
    }

    if (p == 1) {
      return 1;
    }

    int u = 14;
    u %= p; // Compliant
    return 0;
  }

  int car(int s) {
    if (s >= 0) {
    }
    if(s > 0 ) {
      int x = 14 / s;  // Compliant
    }
  }

  int mar(int s) {
    if (s >= 0) {
      int x = 15 / s; // Compliant
    }
    if (s <= 0) {
      int x = 15 / s; // Compliant - FN
    } else {
      int x = 14 / s; // Compliant
    }
  }

  int par(int s) {
    double weight = 0.0;
    if (weight > 0.0) {
      int dx = s / weight; // Compliant
    }
  }

  void preferredLayoutSize(boolean useBaseline) {
    class Dim {
      int width;
      int height;
    }
    Dim dim = new Dim();
    int maxAscent = 0;
    int maxDescent = 0;
    int width = 14;

    dim.width += width;
    width = maxAscent + maxDescent;
  }

  int getValue() {
    return 0;
  }

  void add(int r) {
    int z1 = 0;
    int z2 = z1 + 15;
    r = 1 / z2; // Compliant
  }

  void alo(int r) {
    int z1 = 0;
    int z2 = z1 * 15;
    r = 1 % z2; // Noncompliant {{Make sure 'z2' can't be zero before doing this modulation.}}
  }

  void arg(int r) {
    int z1 = 0;
    int z2 = z1 * 15;
    r = 1 % z2; // Noncompliant {{Make sure 'z2' can't be zero before doing this modulation.}}
  }

  void qix(boolean b, int r) {
    int z1 = 0;
    if (b) {
      z1 = 3;
    } else {
      r = 1;
    }
    r = 1 / z1; // Noncompliant {{Make sure 'z1' can't be zero before doing this division.}}
  }

  void bar(boolean b, long r) {
    long z1 = 0L;
    if (b) {
      r = 1L;
    } else {
      z1 = 3L;
    }
    r /= z1; // Noncompliant {{Make sure 'z1' can't be zero before doing this division.}}
  }

  void bul(boolean b, int r) {
    int z1 = 14;
    if (b) {
      z1 = 0;
    } else {
      z1 = 52;
    }
    r /= z1; // Noncompliant {{Make sure 'z1' can't be zero before doing this division.}}
  }

  void zul(int r) {
    if (r == 0) {
      int z1 = 14 / r; // Noncompliant {{Make sure 'r' can't be zero before doing this division.}}
    }
    int z2 = 14 / r;
  }

  void tol(int r) {
    if (0 < r) {
      int z1 = 14 % r;
    }
    int z2 = 14 % r; // Compliant - False Negative
  }

  void gol(int r) {
    if (r <= 0) {
      int z1 = 14 / r; // Compliant - False Negative
    }
    int z2 = 14 / r;
  }

  void tul(int r) {
    if (r > 0) {
      int z1 = 14 % r;
    }
    int z2 = 14 % r; // Compliant - False Negative
  }

  void gon(int r) {
    if (0 >= r) {
      int z1 = 14 / r; // Compliant - False Negative
    }
    int z2 = 14 / r;
  }

  void gor(int r) {
    if (r != 0) {
      int z1 = 14 / r;
    }
    int z2 = 14 / r; // Noncompliant {{Make sure 'r' can't be zero before doing this division.}}
  }

  void goo(int r) {
    if (!(r != 0)) {
      int z1 = 14 / r; // Noncompliant {{Make sure 'r' can't be zero before doing this division.}}
    }
    int z2 = 14 / r; // Compliant
  }

  void gra(boolean b, int r) {
    int z1 = 0;
    if (b) {
      r = 1;
    } else {
      z1 = 3;
    }
    r = 1 % z1; // Noncompliant {{Make sure 'z1' can't be zero before doing this modulation.}}
  }

  void gou(boolean b, float r) {
    float z1 = 0.0f;
    if (b) {
      z1 = 3.0f;
    } else {
      r = 1.0f;
    }
    r %= z1; // Noncompliant {{Make sure 'z1' can't be zero before doing this modulation.}}
  }

  void woo(boolean b) {
    Long myLong = null;
    if (b) {
      myLong = 0L;
    }
    if (myLong != null) {
      int x = 42 / myLong; // Noncompliant
    }
  }
}
