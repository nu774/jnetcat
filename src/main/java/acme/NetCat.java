package acme;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetCat {
    private static void copy(ReadableByteChannel src, WritableByteChannel sink) throws Exception {
        final var buffer = ByteBuffer.allocateDirect(32768);
        while (src.read(buffer) > 0) {
            buffer.flip();
            while (buffer.remaining() > 0)
                sink.write(buffer);
            buffer.clear();
        }
    }
    private static void usage() {
        System.err.println("usage: jnetcat [tcp:host:port|unix:path]");
        System.exit(1);
    }
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
        }
        try {
            final var tokens = args[0].split(":", 2);
            SocketAddress address = null;
            if (tokens.length < 2)
                usage();
            if (tokens[0].compareToIgnoreCase("tcp") == 0) {
                URI uri = new URI("tcp://" + tokens[1]);
                address = new InetSocketAddress(uri.getHost(), uri.getPort());
            } else if (tokens[0].compareToIgnoreCase("unix") == 0) {
                address = UnixDomainSocketAddress.of(tokens[1]);
            } else {
                usage();
            }
            try (final var stdin = new FileInputStream(FileDescriptor.in).getChannel();
                 final var stdout = new FileOutputStream(FileDescriptor.out).getChannel();
                 final var socket = SocketChannel.open(Objects.requireNonNull(address)))
            {
                final var exceptionHolder = new AtomicReference<Exception>();
                final var executor = Executors.newSingleThreadExecutor();
                try {
                    final var thread = Thread.currentThread();
                    executor.execute(() -> {
                        try {
                            copy(socket, stdout);
                        } catch (Exception e) {
                            exceptionHolder.set(e);
                        } finally {
                            if (!executor.isShutdown())
                                thread.interrupt();
                        }
                    });
                    try {
                        copy(stdin, socket);
                    } catch (InterruptedException ignored) {}
                } finally {
                    if (socket.isConnected())
                        socket.shutdownOutput();
                    executor.shutdown();
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                    final var ex = exceptionHolder.get();
                    if (ex != null) {
                        throw ex;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e);
            System.exit(2);
        }
    }
}
