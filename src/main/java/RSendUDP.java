package main.java;

import edu.utulsa.unet.RSendUDPI;
import edu.utulsa.unet.UDPSocket;


import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class RSendUDP implements Closeable, RSendUDPI {

  private static int H_LEN = 5;

  int mode = 0;
  long windowSize = 256;
  String fileName;
  int portNum = 12987;
  InetSocketAddress currentReceiver = new InetSocketAddress("localhost", portNum);
  long timeout = 1000;

  /**
   * Returns a byte-buffer representing the ith frame in a sequence of frames to send the input
   * msg. Each frame is assumed to be of size framesize.
   * @param msg the total message being sent
   * @param i the sequence number of this frame
   * @param frameSize the size of each frame
   * @return a byte-buffer for the ith frame in the sequence
   */
  public static byte[] formatPacket(String msg, int i, int frameSize) {
    int index = i*(frameSize-H_LEN);
    int length = Math.min((i+1)*(frameSize-H_LEN), msg.length());
    byte[] frame = new byte[frameSize];

    for(int j = H_LEN; index + j - H_LEN < length; j++){
      frame[j] = (byte) msg.charAt(index + j - H_LEN);
    }
    ByteBuffer.wrap(frame).putInt(i);

    frame[H_LEN - 1] = (byte) ((length == msg.length()) ? 0 : 1);

    return frame;
  }

  /** Releases any acquired system resources. */
  public void close() throws IOException {
  }

  /**
   * Returns the name of the file being sent.
   *
   * @return the name of the file being sent
   */
  public String getFilename() {
    return this.fileName;
  }

  /**
   * Returns the port number of the receiver.
   *
   * @return the port number of the receiver
   */
  public int getLocalPort() {
    return this.portNum;
  }

  /**
   * Returns the selected ARQ algorithm where {@code 0} is stop-and-wait and
   * {@code 1} is
   * sliding-window.
   *
   * @return the selected ARQ algorithm
   */
  public int getMode() {
    if(this.mode == 0 ){
      return 0;
    }
    else if (this.mode == 1) {
      return 1;
    }
    return 0;
  }

  /**
   * Returns the size of the window in bytes when using the sliding-window
   * algorithm.
   *
   * @return the size of the window in bytes for the sliding-window algorithm
   */
  public long getModeParameter() {
    return this.windowSize;
  }

  /**
   * Returns the address (hostname) of the receiver.
   *
   * @return the address (hostname) of the receiver
   */
  public InetSocketAddress getReceiver() {
    return this.currentReceiver;
  }

  /**
   * Returns the ARQ timeout in milliseconds.
   *
   * @return the ARQ timeout
   */
  public long getTimeout() {
    return this.timeout;
  }

  /**
   * Sends the pre-selected file to the receiver.
   *
   * @return {@code true} if the file is successfully sent
   */


  public boolean sendFile() {
    if (this.getMode() == 0) { // Checks if the selected ARQ algorithm is stop-and-wait.
      try {
        DatagramSocket socket = connect(); // Opens a DatagramSocket connection (socket) by calling the connect()
        byte[] buffer = new byte[socket.getSendBufferSize()];
        FileInputStream lyrics = new FileInputStream(this.fileName);
        int bytesRead;
        while ((bytesRead = lyrics.read(buffer)) != -1) { // reads data from the file (this.fName) into a byte buffer
          // (buffer).
          if (bytesRead < buffer.length) {
            buffer = Arrays.copyOf(buffer, bytesRead);
          }
          if (!send(socket, buffer,7)) { // Sends the buffer over the socket using the send() method.
            lyrics.close();
            return false;
          }
        }
        // Closes the file input stream (lyrics) and returns true if successful
        lyrics.close();
        return true;
      } catch (IOException e) {
        System.out.println(e);
        return false;
      }
    }

    else if (this.getMode() == 0 || this.getMode() == 1) { // Checks if the selected ARQ algorithm is sliding-window.
      try {
        DatagramSocket socket = connect(); // Opens a DatagramSocket connection (socket) by calling the connect()
        // method.
        FileInputStream fileInputStream = new FileInputStream(this.getFilename());
        byte[] buffer = new byte[200];
        int bytesRead;
        Queue<byte[]> framesToSend = new LinkedList<>();// LinkedList provides FIFO ordering of elements (This enables in order delivery needed for both algorithms)

        int frameCount = 0;

        // Reads data from the file (this.fName) into a byte buffer (buffer).
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
          if (bytesRead < buffer.length) {
            buffer = Arrays.copyOf(buffer, bytesRead);
          }
          byte[] frame = Arrays.copyOf(buffer, bytesRead);
          frameCount++;
          framesToSend.add(frame); // PopulaStes a queue (framesToSend) with frames read from the file.
        }
        System.out.println("Total frames to send: " + frameCount);

        System.out.println("Starting to send file");
        int currentFrame = 0;
        // Enters a loop to send frames and handle acknowledgments.
        while (!framesToSend.isEmpty() && currentFrame <= frameCount) {
          // Sends frames from framesToSend queue if the window is not full and there are
          // frames to send.
          byte[] fullMsg = formatPacket(fileName, currentFrame, (int)windowSize);
          System.out.println("Sending frame: " + currentFrame + " of length: " + fullMsg.length);
          send(socket, fullMsg, frameCount);
          currentFrame++;
          System.out.println("Waiting for acknowledgment...");
        }
        //if FramesToSend Queue is empty (which should be completed after all ACKS recieved by sender) then we have sent all frames
        System.out.println("File sending completed");
        socket.close();
        fileInputStream.close();
        return true; // All frames have been sent and acknowledged
      } catch (IOException e) {
        System.out.println("Error: " + e.getMessage());
        return false;
      }
    }
    return false;
  }

  // Method to add sequence number to packet
  private byte[] addSeqNumberToPacket(byte[] frame, int currentFrame) {
    // Allocate buffer with space for sequence number and frame data
    ByteBuffer buffer = ByteBuffer.allocate(4 + frame.length); // 4 bytes for sequence number

    // Add sequence number to the beginning of the buffer
    buffer.putInt(currentFrame);

    // Add frame data after the sequence number
    buffer.put(frame);
    System.out.println("Frame " + currentFrame + "'s sequanence number added to header of packet");
    // Return byte array from buffer
    return buffer.array();
  }

  /**
   * Sets the name of the file being sent.
   *
   * @param fname the name of the file being sent
   */
  public void setFilename(String fname) {
    this.fileName = fname;
  }

  /**
   * Sets the port number of the receiver.
   *
   * @param port the port number of the receiver
   * @return {@code true} if the intended port of the receiver is set to the input
   *         port
   */
  public boolean setLocalPort(int port) {
    if (0 < port && port < 65535) {
      this.portNum = port;
      return true;
    } else {
      return false;
    }

  }

  /**
   * Sets the selected ARQ algorithm where {@code 0} is stop-and-wait and
   * {@code 1} is
   * sliding-window.
   *
   * @param mode the selected ARQ algorithm
   * @return {@code true} if the ARQ algorithm is set to the input mode
   */
  public boolean setMode(int mode) {
    if (mode == 0) {
      this.mode = 0;
      return true;
    }
    else if (mode == 1 ) {
      this.mode = 1;
      return true;
    }
    return false;
  }

  /**
   * Sets the size of the window in bytes when using the sliding-window algorithm.
   *
   * @param n the size of the window in bytes for the sliding-window algorithm
   * @return {@code true} if the window size is set to the input n
   */
  public boolean setModeParameter(long n) {
    this.windowSize = n;
    return n > 0;

  }

  /**
   * Sets the address (hostname) of the receiver.
   *
   * @param receiver the address (hostname) of the receiver
   * @return {@code true} if the intended address of the receiver is set to the
   *         input receiver
   */
  public boolean setReceiver(InetSocketAddress receiver) {
    this.currentReceiver = receiver;
    return true;

  }

  /**
   * Sets the ARQ timeout in milliseconds.
   *
   * @param timeout the ARQ timeout
   * @return {@code true} if the ARQ timeout is set to the input timeout
   */
  public boolean setTimeout(long timeout) {
    this.timeout = timeout;
    return timeout > 0;

  }

  /**
   * Returns an established socket connection.
   *
   * @return an established DatagramSocket connection
   */

  private DatagramSocket connect() throws IOException {
    return new UDPSocket(this.getLocalPort()); // Creates and binds the datagram socket (UDPSocket in this case) to the

  }

  /**
   * Sends buffer over socket connection.
   *
   * @param socket an established DatagramSocket connection
   * @param buffer the buffer to send over the socket
   * @return {@code true} if the buffer is successfully sent over the socket
   */
  private boolean send(DatagramSocket socket, byte[] buffer, int count) {
    try {
      DatagramPacket packet = new DatagramPacket(
              buffer, buffer.length, this.getReceiver().getAddress(), this.getReceiver().getPort());
      System.out.println("Sending packet to receiver at port portnumber " + this.getReceiver().getPort() + " whose address is: " + this.getReceiver().getAddress());
      socket.send(packet);
      System.out.println("Packet sent");
      Thread.sleep(250);
      return true;
    } catch (InterruptedException | IOException e) {
      System.out.println(e);
      return false;
    }
  }
}