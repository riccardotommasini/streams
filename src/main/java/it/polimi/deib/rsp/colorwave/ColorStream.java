package it.polimi.deib.rsp.colorwave;

import com.github.jsonldjava.core.JsonLdOptions;
import lombok.extern.java.Log;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapStd;
import org.apache.jena.riot.writer.JsonLDWriter;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import spark.Service;
import spark.Spark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static spark.Spark.get;

@Log
@WebSocket
public class ColorStream {

    private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();
    private final Boolean naked;
    private final int seed;
    private final String color;
    private final String jsonld;
    private final int http_port;
    private final int ws_port;
    private final String colorClass;
    boolean running = false;
    String ttl;
    private Random random;

    public ColorStream(String color, int seed, Boolean naked) throws IOException {
        this.color = color;
        this.colorClass = color.substring(0, 1).toUpperCase() + color.substring(1);
        this.ttl = " a <www.rw.org/streams#" + colorClass + "> .\n";
        this.jsonld = IOUtils.toString(ColorStream.class.getClassLoader().getResourceAsStream("body.jsonld")).replace("$COLOR", colorClass);
        this.random = new Random(seed);
        this.seed = seed;
        this.naked = naked;

        if ("green".equals(color)) {
            this.http_port = 2255;
            this.ws_port = 2552;
        } else if ("blue".equals(color)) {
            this.http_port = 3255;
            this.ws_port = 2553;
        } else {
            this.http_port = 1255;
            this.ws_port = 2551;
        }

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
                            e.sendString(new String(jsonld).replace("$INSTANCE", "" + UUID.randomUUID()));
                        } catch (Exception ex) {
                        }
                    });
                    try {
                        Thread.sleep(1000 * random.nextInt(9));
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

    public void ignite() throws IOException {

        log.info("Running a "
                + "[" + colorClass + "] Stream - " +
                "WebSocket at [/" + color + "][" + ws_port + "]" +
                (naked ? " - Naked " : " - VoCaLS File at port [/" + color + "][" + http_port + "] ")
        );

        InputStream inputStream = ColorStream.class.getClassLoader().getResourceAsStream(color + ".ttl");

        Model model = ModelFactory.createDefaultModel().read(inputStream, "http://" + color + "stream:" + http_port + "/" + color, "TTL");

        JsonLDWriter jsonLDWriter = new JsonLDWriter(RDFFormat.JSONLD_COMPACT_FLAT);
        PrefixMap pm = new PrefixMapStd();
        JsonLDWriteContext context = new JsonLDWriteContext();
        JsonLdOptions options = new JsonLdOptions();

        options.setPruneBlankNodeIdentifiers(true);
        context.setOptions(options);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        model.getNsPrefixMap().forEach(pm::add);
        jsonLDWriter.write(outputStream, DatasetGraphFactory.create(model.getGraph()), pm, "", context);

        String g = outputStream.toString();

        if (!naked) {
            Spark.port(http_port);
            get(color, (request, response) -> {
                response.type(ContentType.APPLICATION_JSON.getMimeType());
                return g;
            });
        }

        Service ws = Service.ignite();
        ws.port(ws_port).webSocket("/" + color, this);
        ws.init();

    }

    public static void main(String[] args) throws URISyntaxException, IOException {
        if (args.length == 2) {
            String color = args[0].toLowerCase();
            Boolean naked = "naked".equals(args[1].toLowerCase());
            new ColorStream(color, color.hashCode(), naked).ignite();
        } else if (args.length == 1) {
            String color = args[0].toLowerCase();
            new ColorStream(color, color.hashCode(), false).ignite();
        } else {
            new ColorStream("red", 1, false).ignite();
        }
    }
}