package javax.annotation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


@interface Nonnull {}

@interface Nullable {}

public class CrossProcedural {
  
  public void callerOpen() {
    try{
      OutputStream stream = open("myfile.txt");  // Noncompliant, the stream resource is never closed
    }catch(IOException e){}
  }
  
  public void callerClose() {
    try{
      OutputStream stream = new FileOutputStream("myfile.txt");  // Compliant
      close(stream);
    }catch(IOException e){}
  }
  
  public void callerWrongClose() {
    try{
      OutputStream stream = new FileOutputStream("myfile.txt");  // Not yet -- Noncompliant, the stream resource is never closed
      wrongClose(stream);
    }catch(IOException e){}
  }

  public void close(OutputStream stream) throws IOException {
    stream.close();
  }

  public void wrongClose(OutputStream stream) {
    //Nothing is done
  }

  public OutputStream open(String fileName) throws IOException {
    return new FileOutputStream(fileName);   //As the stream is not stored in any class field, it MUST be closed by calling methods
  }

}
