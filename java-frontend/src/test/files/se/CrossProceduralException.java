package javax.annotation;

import java.text.Format;
import java.text.ParseException;

public class ThrowExceptions {

  private Format format;

  public void callerParse() {
    try {
      Object object = parse("object");
      object.toString();  // Compliant since never reached when exception
    } catch (ConversionException e) {
      logError(e);
    }
  }

  private Object parse(String object) throws Exception {
    Object result = format.parseObject(object);
    if (result == null) {
      throw new ConversionException();
    }
    return result;
  }

  private static class ConversionException extends Exception {}
}
