import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import spark.Spark;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static spark.Spark.webSocket;

@WebSocket
public class NakedStream {

    private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();
    boolean running = false;
    String body;

    public NakedStream(URI file) throws IOException {
        body = IOUtils.toString(file);
    }

    @OnWebSocketConnect
    public void connected(Session session) {
        sessions.add(session);
        List<RemoteEndpoint> endpoints =
                sessions.stream().map(Session::getRemote).collect(Collectors.toList());
        if (!running)
            new Thread(() -> {
                while (true) {
                    endpoints.forEach(e -> {
                        try {
                            e.sendString(body);
                        } catch (Exception ex) {
                        }
                    });
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
    }

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        sessions.remove(session);
    }

    @OnWebSocketMessage
    public void message(Session session, String message) throws IOException {
        System.out.println("Got: " + message);   // Print message
        session.getRemote().sendString(message); // and send it back
    }

    public static void main(String[] args) throws URISyntaxException, IOException {

        Spark.port(4040);

        URL resource = NakedStream.class.getClassLoader().getResource("input.json");
        webSocket("/stream1", new NakedStream(resource.toURI()));
        Spark.init();
    }
}