package main.java;

import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RReceiveUDP implements Closeable, RReceiveUDPI {

  private static final int DEFAULT_TIMEOUT = 10_000;
  int mode = 0;
  long windowSize = 256;
  String fileName;
  int portNum = 12987;

  /**
   * Returns a byte-buffer representing an ACK for a frame whose sequence number is seq, where
   * seq starts at 0 for the first frame.
   * @param seq the sequence number of the frame
   * @return a byte-buffer for an ACK on sequence number seq
   */
  public static byte[] formatACK(int seq) {
    ByteBuffer buffer = ByteBuffer.allocate(4);
    buffer.putInt(seq);
    return buffer.array();
  }

  /** Releases any acquired system resources. */
  public void close() throws IOException {}

  /**
   * Returns the name of the file being sent.
   * @return the name of the file being sent
   */
  public String getFilename() {
    return fileName;
  }

  /**
   * Returns the port number of the receiver.
   * @return the port number of the receiver
   */
  public int getLocalPort() {
    return portNum;
  }

  /**
   * Returns the selected ARQ algorithm where {@code 0} is stop-and-wait and {@code 1} is
   * sliding-window.
   * @return the selected ARQ algorithm
   */
  public int getMode() {
    return this.mode;
  }

  /**
   * Returns the size of the window in bytes when using the sliding-window algorithm.
   * @return the size of the window in bytes for the sliding-window algorithm
   */
  public long getModeParameter() {
    return windowSize;
  }

  /**
   * Listens for incoming packets and saves the data to the pre-selected file.
   * @return {@code true} if the file is successfully received
   */
  public boolean receiveFile() {
    try{
      // Stop and Wait
      if(mode == 0){
        DatagramSocket ds = connect(); // connect
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);

        int maxTimeouts = 3;
        int timeouts = 0;

        int desiredPacket = 0;
        while (timeouts < maxTimeouts) {

          byte[] buffer = new byte[ds.getSendBufferSize()];
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          try{
            ds.setSoTimeout(5_000);
            ds.receive(packet);
            timeouts = 0;
            ByteBuffer wrapper = ByteBuffer.wrap(packet.getData());
            int fNum = wrapper.getInt();
            if(fNum <= desiredPacket){
              if (fNum == desiredPacket) {
                desiredPacket++;

                System.out.println("Received " + packet.getLength() + " bytes");
                System.out.println("Recived Bytes");

                fileOutputStream.write(packet.getData(), 5, packet.getLength() - 5);
              }

              byte[] ack = formatACK(fNum);
              DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, packet.getAddress(), packet.getPort());
              ds.send(ackPacket);
              System.out.println("Sent acknowledgment");

              if (packet.getData()[4] == 0) break;
            }
          } catch (IOException e){
            timeouts++;
          }
        }
        ds.close();
        fileOutputStream.close();
        return (timeouts < maxTimeouts);
      }
      // Sliding Window
      if(mode == 1){
        DatagramSocket ds = connect();
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        ArrayList<byte[]> incomingList = new ArrayList<>();
        int maxTimeouts = 3;
        int timeouts = 0;
        while (timeouts < maxTimeouts) {
          int windowSize = Math.min(ds.getSendBufferSize(), (int) getModeParameter());
          byte[] buffer = new byte[windowSize];
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          try {
            ds.setSoTimeout(5_000);
            ds.receive(packet);
            timeouts = 0;

            ByteBuffer wrapper = ByteBuffer.wrap(packet.getData());
            int fNum = wrapper.getInt();

            while (incomingList.size() <= fNum) incomingList.add(null);
            if (incomingList.get(fNum) == null) {
              incomingList.set(fNum, packet.getData());
            }

            byte[] ack = formatACK(fNum);
            DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, packet.getAddress(), packet.getPort());
            ds.send(ackPacket);
            System.out.println("Sent acknowledgment");

            int i = 0;
            for (; i < incomingList.size() && incomingList.get(i) != null; i++);
            if (i == incomingList.size() && incomingList.get(i - 1)[4] == 0) break;
          } catch (IOException e) {
            timeouts++;
          }
        }

        if (timeouts >= maxTimeouts) {
          ds.close();
          fileOutputStream.close();
          return false;
        }

        for (byte[] data : incomingList) {
          fileOutputStream.write(data, 5, data.length - 5);
        }

        ds.close();
        fileOutputStream.close();
      }
      return true;
    } catch (IOException ex){
      return false;
    }

  }

  /**
   * Sets the name of the file being sent.
   * @param fname the name of the file being sent
   */
  public void setFilename(String fname) {
    fileName = fname;
  }

  /**
   * Sets the port number of the receiver.
   * @param port the port number of the receiver
   * @return {@code true} if the intended port of the receiver is set to the input port
   */
  public boolean setLocalPort(int port) {
    if(port >= 0 && port <= 65535){
      portNum = port;
      return true;
    }else{
      return false;
    }
  }

  /**
   * Sets the selected ARQ algorithm where {@code 0} is stop-and-wait and {@code 1} is
   * sliding-window.
   * @param mode the selected ARQ algorithm
   * @return {@code true} if the ARQ algorithm is set to the input mode
   */
  public boolean setMode(int mode) {
    if (mode == 0 || mode == 1){
      this.mode = mode;
      return true;
    }else{
      System.out.println("Invalid Mode");
      return false;
    }
  }

  /**
   * Sets the size of the window in bytes when using the sliding-window algorithm.
   * @param n the size of the window in bytes for the sliding-window algorithm
   * @return {@code true} if the window size is set to the input n
   */
  public boolean setModeParameter(long n) {
    if(n >= 1){
      windowSize = n;
      return true;
    }else{
      return false;
    }
  }

  /**
   * Returns an established socket connection.
   * @return an established DatagramSocket connection
   */
  private DatagramSocket connect() throws IOException {
    return new UDPSocket(this.getLocalPort());
  }

  /**
   * Writes data from the socket connection into the buffer.
   * @param socket an established DatagramSocket connection
   * @param buffer the buffer to write data into
   * @return the packet that was received or {@code null} if an error occurred
   */
  private DatagramPacket receive(DatagramSocket socket, byte[] buffer) {
    try {
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      socket.setSoTimeout(DEFAULT_TIMEOUT);
      socket.receive(packet);
      return packet;
    } catch (IOException e) {
      System.out.println(e);
      return null;
    }
  }
}