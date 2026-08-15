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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Speaks MCP on behalf of a running Tuatara window.
 *
 * The same archive, started with --mcp, and no Swing in sight. An agent's client launches this,
 * talks JSON-RPC to it down a pipe, and this forwards each call to the window over the loopback
 * connection {@link AgentServer} is listening on. If no window is running it starts one and waits.
 *
 * Deliberately thin. It knows about the protocol and nothing about machines: the tools, their
 * descriptions and their behaviour all live in the window, so there is no second copy of any of it
 * to drift.
 */
public final class McpAdapter
{
    /**
     * The protocol version this speaks.
     */
    private static final String PROTOCOL = "2024-11-05";

    /**
     * How long to wait for a window to appear after starting one.
     */
    private static final long LAUNCH_TIMEOUT_MS = 40000;

    /**
     * Where diagnostics go. Never stdout: that carries the protocol.
     */
    private static final PrintStream LOG = System.err;

    /**
     * Where to send calls, once a window has been found.
     */
    private static int port;

    /**
     * The token that window is expecting.
     */
    private static String token;

    /**
     * Not instantiable.
     */
    private McpAdapter() { }

    /**
     * Run the adapter until its input closes.
     * @param args Command line arguments, after --mcp.
     * @throws Exception If the conversation could not be carried on.
     */
    public static void run(String[] args) throws Exception
    {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        OutputStream rawOut = System.out;
        String line;
        while ((line = in.readLine()) != null)
        {
            line = line.trim();
            if (line.isEmpty())
            {
                continue;
            }
            Object request;
            try
            {
                request = Json.parse(line);
            }
            catch (Json.SyntaxException e)
            {
                write(rawOut, Json.object("jsonrpc", "2.0", "id", null,
                            "error", Json.object("code", Integer.valueOf(-32700),
                                "message", "invalid JSON: " + e.getMessage())));
                continue;
            }
            Object response = handle(request);
            if (response != null)
            {
                write(rawOut, response);
            }
        }
    }

    private static void write(OutputStream out, Object message) throws IOException
    {
        out.write((Json.write(message) + "\n").getBytes("UTF-8"));
        out.flush();
    }

    /* ---------------------------------------------------------------- *
     * The conversation
     * ---------------------------------------------------------------- */

    private static Object handle(Object request)
    {
        String method = Json.str(request, "method", "");
        Object id = Json.member(request, "id");
        boolean notification = !Json.has(request, "id");

        try
        {
            if (method.equals("initialize"))
            {
                // Connecting here rather than at startup: a client that launches this and then does
                // nothing should not open a window nobody asked for.
                connect();
                Object tools = get("/tools");
                return reply(id, Json.object(
                            "protocolVersion", PROTOCOL,
                            "capabilities", Json.object("tools", Json.object()),
                            "serverInfo", Json.object("name", "tuatara",
                                "version", Json.str(tools, "version", tuataraTMSim.Global.VERSION)),
                            "instructions", Json.str(tools, "instructions", "")));
            }
            if (method.startsWith("notifications/") || notification)
            {
                return null;
            }
            if (method.equals("ping"))
            {
                return reply(id, Json.object());
            }
            if (method.equals("tools/list"))
            {
                Object tools = get("/tools");
                return reply(id, Json.object("tools", Json.member(tools, "tools")));
            }
            if (method.equals("tools/call"))
            {
                Object params = Json.member(request, "params");
                String name = Json.str(params, "name", "");
                Object arguments = Json.member(params, "arguments");
                Object answer = post("/call",
                        Json.object("tool", name, "arguments",
                            arguments == null? Json.object() : arguments));

                if (Json.has(answer, "error"))
                {
                    return reply(id, Json.object(
                                "isError", Boolean.TRUE,
                                "content", Json.array(Json.object(
                                        "type", "text", "text", Json.str(answer, "error", "failed")))));
                }
                return reply(id, Json.object("content", content(Json.member(answer, "result"))));
            }
            return reply(id, null, -32601, "unsupported method: " + method);
        }
        catch (AgentException e)
        {
            if (notification)
            {
                return null;
            }
            return reply(id, Json.object(
                        "isError", Boolean.TRUE,
                        "content", Json.array(Json.object("type", "text", "text", e.getMessage()))));
        }
        catch (Exception e)
        {
            if (notification)
            {
                return null;
            }
            return reply(id, null, -32603,
                    e.getClass().getSimpleName() + (e.getMessage() == null? "" : ": " + e.getMessage()));
        }
    }

    /**
     * Turn a tool's answer into the content blocks a client expects. Almost everything is JSON
     * text; a rendered machine is an image, and sending it as one is the difference between the
     * agent seeing the diagram and seeing a wall of base64.
     * @param result What the tool returned.
     * @return The content blocks.
     */
    private static List<Object> content(Object result)
    {
        List<Object> blocks = new ArrayList<Object>();
        if (result instanceof Map && Json.has(result, "image_png_base64"))
        {
            Map<String, Object> rest = Json.object();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)result).entrySet())
            {
                if (!"image_png_base64".equals(entry.getKey()))
                {
                    rest.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            blocks.add(Json.object("type", "image",
                        "data", Json.str(result, "image_png_base64", ""),
                        "mimeType", "image/png"));
            blocks.add(Json.object("type", "text", "text", Json.writePretty(rest, 2)));
            return blocks;
        }
        blocks.add(Json.object("type", "text", "text", Json.writePretty(result, 2)));
        return blocks;
    }

    private static Object reply(Object id, Object result)
    {
        return Json.object("jsonrpc", "2.0", "id", id, "result", result);
    }

    private static Object reply(Object id, Object ignored, int code, String message)
    {
        return Json.object("jsonrpc", "2.0", "id", id,
                "error", Json.object("code", Integer.valueOf(code), "message", message));
    }

    /* ---------------------------------------------------------------- *
     * Finding, or starting, a window
     * ---------------------------------------------------------------- */

    /**
     * Make sure there is a window to talk to, starting one if there is not.
     * @throws AgentException If no window could be reached.
     */
    private static synchronized void connect() throws AgentException
    {
        if (readHandshake() && alive())
        {
            return;
        }
        LOG.println("tuatara: no window is running; starting one");
        launch();

        long deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline)
        {
            try
            {
                Thread.sleep(300);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
            if (readHandshake() && alive())
            {
                LOG.println("tuatara: connected on port " + port);
                return;
            }
        }
        throw new AgentException(
                "Could not reach Tuatara. Start it, check that assistant access is on under the "
              + "Configuration menu, and try again. The connection details are read from "
              + AgentServer.handshakeFile() + ".");
    }

    /**
     * Read the port and token a running window left behind.
     * @return true if the file was there and could be read.
     */
    private static boolean readHandshake()
    {
        File file = AgentServer.handshakeFile();
        if (!file.isFile())
        {
            return false;
        }
        try
        {
            FileInputStream in = new FileInputStream(file);
            try
            {
                Object doc = Json.parse(new String(readAll(in), "UTF-8"));
                port = (int)Json.num(doc, "port", 0);
                token = Json.str(doc, "token", null);
                return port > 0 && token != null;
            }
            finally
            {
                in.close();
            }
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Determine whether the window named by the handshake file is still there. The file outlives a
     * window that was killed rather than closed, so its presence proves nothing on its own.
     * @return true if something answered.
     */
    private static boolean alive()
    {
        try
        {
            Object hello = get("/hello");
            return Json.bool(hello, "ok", false);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Start the program, in its ordinary windowed mode.
     * @throws AgentException If it could not be started.
     */
    private static void launch() throws AgentException
    {
        try
        {
            File self = new File(McpAdapter.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            String java = new File(new File(System.getProperty("java.home"), "bin"), "java")
                    .getAbsolutePath();
            List<String> command = new ArrayList<String>();
            command.add(java);
            if (self.isFile())
            {
                command.add("-jar");
                command.add(self.getAbsolutePath());
            }
            else
            {
                // Running from class files, which is what happens during development.
                command.add("-cp");
                command.add(self.getAbsolutePath());
                command.add("tuataraTMSim.MainWindow");
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.start();
        }
        catch (Exception e)
        {
            throw new AgentException("Could not start Tuatara: "
                    + e.getClass().getSimpleName()
                    + (e.getMessage() == null? "" : " -- " + e.getMessage())
                    + ". Start it yourself and try again.");
        }
    }

    /* ---------------------------------------------------------------- *
     * Talking to the window
     * ---------------------------------------------------------------- */

    private static Object get(String path) throws Exception
    {
        return exchange("GET", path, null);
    }

    private static Object post(String path, Object body) throws Exception
    {
        return exchange("POST", path, Json.write(body));
    }

    private static Object exchange(String method, String path, String body) throws Exception
    {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try
        {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            // No read timeout: a batch of a million-step tests is allowed to take its time, and the
            // window enforces its own budget anyway.
            connection.setReadTimeout(0);
            if (token != null)
            {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null)
            {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.getBytes("UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = connection.getOutputStream();
                try
                {
                    out.write(bytes);
                }
                finally
                {
                    out.close();
                }
            }
            InputStream in = connection.getResponseCode() >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            if (in == null)
            {
                throw new AgentException("Tuatara gave no answer (HTTP "
                        + connection.getResponseCode() + ").");
            }
            try
            {
                return Json.parse(new String(readAll(in), "UTF-8"));
            }
            finally
            {
                in.close();
            }
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0)
        {
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }
}
