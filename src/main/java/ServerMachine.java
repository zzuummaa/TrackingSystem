import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

public class ServerMachine {
    public static enum ServerState {
        accept,
        connected,
        closing,
        unknown
    }

    private ServerSocket serverSocket;

    private Socket client = null;
    private BufferedReader reader = null;
    private OutputStream os = null;
    private long lastPingTime;
    private int pingPeriod;

    private ServerState state = ServerState.accept;

    private Consumer<String> onMessage;

    public ServerMachine(int port, Consumer<String> onMessage) throws IOException {
        serverSocket = new ServerSocket(port, 1);
        this.onMessage = onMessage;
        this.pingPeriod = 4 * 1000;

        System.out.println("Server accepting");
    }

    public ServerState handleState() throws IOException {
        return handleState(state);
    }

    public ServerState handleState(ServerState serverState) throws IOException {
        switch (serverState) {
            case accept:
                client = serverSocket.accept();
                //client.setSoTimeout(pingPeriod);

                reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                os = client.getOutputStream();

                state = ServerState.connected;
                System.out.println("Client accepted: " + client.getInetAddress());
                break;

            case connected:
                String line = null;

                long currTime = System.currentTimeMillis();
                if (currTime - lastPingTime > pingPeriod) {
                    lastPingTime = currTime;

                    try {
                        os.write("ping\r\n".getBytes());
                        os.flush();
                    } catch (IOException e) {
                        System.out.println("Client closing: " + client.getInetAddress());
                        state = ServerState.closing;
                        return state;
                    }
                }

                try {
                    if (!reader.ready()) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return serverState;
                    }

                    line = reader.readLine();
                    System.out.println(line);

                } catch (SocketTimeoutException e) {
                    return state;
                } catch (IOException e) {
                    System.out.println("Client closing: " + client.getInetAddress());
                    state = ServerState.closing;
                    return state;
                }

                onMessage.accept(line);
                break;

            case closing:
                try {
                    lastPingTime = 0;
                    os.close();
                    reader.close();
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println("Server accepting");
                state = ServerState.accept;
                break;
        }

        return state;
    }
}
