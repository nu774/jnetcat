# jnetcat

This is a tiny program implemented in Java, similar to netcat. However, jnetcat doesn't support many options like netcat.

jnetcat walks only on client mode, and takes only one argument as a remote adderess, in the form of either tcp:host:port or unix:path.

jnetcat always uses half-close upon disconnection (similar to netcat -N behavior).

## Implementation Details

In bidirectional relay, reading from both the socket and standard input are blocking. To execute both of them concurrently, you typically need threads or some I/O multiplexing mechanism. Since maxium concurrency is limited to only two, jnetcat just uses threads.

If the standard input side reaches EOF first, jnetcat initiates a half-close on the socket via shutdown call. Once the remote side also completes, jnetcat finishes the connection. It is essential to use half-close to signal EOF to the remote side and receive the remaining remote packets to completion.

If the connection is closed from the remote side, it's desirable to immediately quit reading from standard input. However, whether you can interrupt a blocking read from standard input is OS or implementation dependent.

In JDK 17, it seems possible to interrupt standard input on Linux but not on Windows. Even on Linux, interrupting System.in seems not possible on JDK 17. Therefore, jnetcat uses nio's FileChannel instead which implements InterruptibleChannel interface.
