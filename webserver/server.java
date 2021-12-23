import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

  public static void main(String[] args) throws IOException {
    String hostname = InetAddress.getLocalHost().getHostName();
    ServerSocket serverSocket = new ServerSocket(8089);
    while (true) {
      Socket clientSocket = serverSocket.accept();
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      String s = in.readLine();
      System.out.println(s);
      while ("\r\n".equals(in.readLine())); 
      if ("GET /hostname HTTP/1.1".equals(s)) {
        out.println("HTTP/1.1 200 OK");
        out.println("Connection: close");
        out.println("Content-Type: text/plain");
        out.println("Content-Length:" + hostname.length());
        out.println();
        out.println(hostname);
      } else {
        out.println("HTTP/1.1 404 Not Found");
        out.println("Connection: close");
        out.println();    
      }
      out.flush();
    }
  }
}
