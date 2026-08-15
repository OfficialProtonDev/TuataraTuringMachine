//  ------------------------------------------------------------------
//
//  Copyright (c) 2006-2007 James Foulds and the University of Waikato
//
//  ------------------------------------------------------------------
//  This file is part of Tuatara Turing Machine Simulator.
//
//  Tuatara Turing Machine Simulator is free software: you can redistribute
//  it and/or modify it under the terms of the GNU General Public License as
//  published by the Free Software Foundation, either version 3 of the License,
//  or (at your option) any later version.
//
//  Tuatara Turing Machine Simulator is distributed in the hope that it will be
//  useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with Tuatara Turing Machine Simulator.  If not, see
//  <http://www.gnu.org/licenses/>.
//
//  author email: jf47 (at) waikato (dot) ac (dot) nz
//
//  ------------------------------------------------------------------

package tuataraTMSim.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The way in.
 *
 * A small HTTP server on the loopback interface, so that a separate process -- the MCP adapter,
 * which the agent actually talks to -- can reach the machines this window is showing. It listens
 * only on 127.0.0.1 and only answers requests carrying a token it wrote to a file in the user's
 * home directory, which is the same posture as any other local development server: enough to stop a
 * stray web page in a browser reading somebody's diagram, and not pretending to be more.
 *
 * There is no dependency here. The JDK has had an HTTP server in it since Java 6, and the alternative
 * was adding a build system to a project that gets by with javac and a makefile.
 */
public final class AgentServer
{
    /**
     * Where the port and token are left for the adapter to find.
     */
    private static final String HANDSHAKE_DIR = ".tuatara";

    /**
     * See {@link #HANDSHAKE_DIR}.
     */
    private static final String HANDSHAKE_FILE = "agent.json";

    /**
     * The running server, or null.
     */
    private static HttpServer server;

    /**
     * The port in use, or 0.
     */
    private static int port;

    /**
     * The token a caller must present.
     */
    private static String token;

    /**
     * Told when the server starts, stops, or handles a call, so the window can show what is going on.
     */
    private static Listener listener;

    /**
     * How many calls have been served since the server started.
     */
    private static int calls;

    /**
     * Notified about the server's comings and goings, so the window can show them.
     */
    public interface Listener
    {
        /**
         * Called when the server starts or stops, and after every call.
         */
        void agentActivity();
    }

    /**
     * Not instantiable.
     */
    private AgentServer() { }

    /**
     * Ask to be told when something happens.
     * @param l The listener.
     */
    public static void setListener(Listener l)
    {
        listener = l;
    }

    /**
     * Determine whether the server is listening.
     * @return true if it is running.
     */
    public static boolean isRunning()
    {
        return server != null;
    }

    /**
     * The port in use.
     * @return The port, or 0 if the server is not running.
     */
    public static int getPort()
    {
        return port;
    }

    /**
     * How many calls have been served.
     * @return The number of calls since the server started.
     */
    public static int getCallCount()
    {
        return calls;
    }

    /**
     * Start listening, on a port the operating system chooses.
     * @throws IOException If the server could not be started.
     */
    public static synchronized void start() throws IOException
    {
        if (server != null)
        {
            return;
        }
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        StringBuilder sb = new StringBuilder();
        for (byte b : random)
        {
            sb.append(String.format("%02x", Byte.valueOf(b)));
        }
        token = sb.toString();

        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", new Router());
        // A small pool, not the caller's thread: a tool call can take a while, and the next one
        // should not have to wait for it.
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        calls = 0;
        writeHandshake();
        fire();
    }

    /**
     * Stop listening, and remove the handshake file.
     */
    public static synchronized void stop()
    {
        if (server == null)
        {
            return;
        }
        server.stop(0);
        server = null;
        port = 0;
        token = null;
        File file = handshakeFile();
        if (file.exists() && !file.delete())
        {
            file.deleteOnExit();
        }
        fire();
    }

    /**
     * The file the adapter reads to find this window.
     * @return The handshake file, which may not exist.
     */
    public static File handshakeFile()
    {
        return new File(new File(System.getProperty("user.home", "."), HANDSHAKE_DIR),
                HANDSHAKE_FILE);
    }

    /**
     * Leave the port and token where the adapter can find them.
     * @throws IOException If the file could not be written.
     */
    private static void writeHandshake() throws IOException
    {
        File file = handshakeFile();
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs())
        {
            throw new IOException("could not create " + dir);
        }
        Map<String, Object> doc = Json.object(
                "port", Integer.valueOf(port),
                "token", token,
                "version", tuataraTMSim.Global.VERSION,
                "started", Long.valueOf(System.currentTimeMillis()));
        FileOutputStream out = new FileOutputStream(file);
        try
        {
            out.write(Json.writePretty(doc, 2).getBytes("UTF-8"));
        }
        finally
        {
            out.close();
        }
        // Best effort: keep the token out of other users' reach on systems that distinguish.
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
    }

    private static void fire()
    {
        final Listener l = listener;
        if (l != null)
        {
            javax.swing.SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    l.agentActivity();
                }
            });
        }
    }

    /* ---------------------------------------------------------------- *
     * Routing
     * ---------------------------------------------------------------- */

    /**
     * Answers the three things the adapter asks for: whether anybody is home, what the tools are,
     * and please run one.
     */
    private static final class Router implements HttpHandler
    {
        public void handle(HttpExchange exchange) throws IOException
        {
            try
            {
                String path = exchange.getRequestURI().getPath();

                // Deliberately open, and says nothing worth knowing. The adapter uses it to tell
                // "no window running" apart from "wrong token", which need different advice.
                if (path.equals("/hello"))
                {
                    respond(exchange, 200, Json.object(
                                "ok", Boolean.TRUE,
                                "name", "tuatara",
                                "version", tuataraTMSim.Global.VERSION));
                    return;
                }

                if (!authorised(exchange))
                {
                    respond(exchange, 403, Json.object("error",
                                "bad or missing token; read it from " + handshakeFile()));
                    return;
                }

                if (path.equals("/tools"))
                {
                    respond(exchange, 200, Json.object(
                                "instructions", Tools.instructions(),
                                "tools", Tools.definitions()));
                    return;
                }

                if (path.equals("/call"))
                {
                    Object body = Json.parse(read(exchange.getRequestBody()));
                    String name = Json.str(body, "tool", "");
                    Object args = Json.member(body, "arguments");
                    calls++;
                    try
                    {
                        Object result = Tools.call(name, args);
                        respond(exchange, 200, Json.object("result", result));
                    }
                    catch (AgentException e)
                    {
                        // The agent's own mistake, and the message is written for it to read.
                        respond(exchange, 200, Json.object("error", e.getMessage()));
                    }
                    catch (Json.SyntaxException e)
                    {
                        respond(exchange, 200, Json.object("error",
                                    "the arguments were not valid JSON: " + e.getMessage()));
                    }
                    catch (Exception e)
                    {
                        respond(exchange, 200, Json.object("error",
                                    e.getClass().getSimpleName()
                                  + (e.getMessage() == null? "" : ": " + e.getMessage())));
                    }
                    finally
                    {
                        fire();
                    }
                    return;
                }

                respond(exchange, 404, Json.object("error", "no such endpoint: " + path));
            }
            catch (Throwable t)
            {
                try
                {
                    respond(exchange, 500, Json.object("error", String.valueOf(t)));
                }
                catch (IOException ignored)
                {
                    // The caller has gone; there is nowhere to report this.
                }
            }
        }

        private boolean authorised(HttpExchange exchange)
        {
            if (token == null)
            {
                return false;
            }
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header != null && header.startsWith("Bearer "))
            {
                return token.equals(header.substring("Bearer ".length()).trim());
            }
            String query = exchange.getRequestURI().getQuery();
            if (query != null)
            {
                for (String part : query.split("&"))
                {
                    if (part.startsWith("token="))
                    {
                        return token.equals(part.substring("token=".length()));
                    }
                }
            }
            return false;
        }

        private String read(InputStream in) throws IOException
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0)
            {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), "UTF-8");
        }

        private void respond(HttpExchange exchange, int code, Object body) throws IOException
        {
            byte[] bytes = Json.write(body).getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            OutputStream out = exchange.getResponseBody();
            try
            {
                out.write(bytes);
            }
            finally
            {
                out.close();
            }
        }
    }
}
