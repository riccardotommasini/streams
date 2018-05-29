import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.rio.jsonld.JSONLDWriterFactory;
import spark.Service;
import spark.Spark;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static spark.Spark.get;

@WebSocket
public class HelperStreamWS {

    private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();
    boolean running = false;
    String body;

    public HelperStreamWS(URI file) throws IOException {
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
        InputStream inputStream = HelperStreamWS.class.getClassLoader().getResourceAsStream("sgraph.ttl");
        RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
        Model model = new LinkedHashModel();
        rdfParser.setRDFHandler(new StatementCollector(model));
        rdfParser.parse(inputStream, "http://localhost:4000/");

        Spark.port(4000);

        get("stream1", (request, response) -> {

            StringWriter out = new StringWriter();
            JSONLDWriterFactory jsonldWriterFactory = new JSONLDWriterFactory();
            RDFWriter rdfWriter = jsonldWriterFactory.getWriter(out);
            rdfWriter.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
            rdfWriter.startRDF();
            model.forEach(rdfWriter::handleStatement);
            rdfWriter.endRDF();

            response.type(ContentType.APPLICATION_JSON.getMimeType());
            return out.toString();
        });

        URL resource = HelperStreamWS.class.getClassLoader().getResource("input.json");
        Service ws = Service.ignite();
        ws.port(4040).webSocket("/stream1", new HelperStreamWS(resource.toURI()));
        ws.init();


    }
}