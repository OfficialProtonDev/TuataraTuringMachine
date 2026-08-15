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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON to talk to an agent.
 *
 * The project builds with javac and a makefile, and pulling in a dependency manager for one data
 * format would be a bigger change to the project than the feature that needs it. Values map onto
 * plain Java: an object is a LinkedHashMap, an array an ArrayList, a number a Long or a Double, and
 * everything else is a String, a Boolean or null.
 */
public final class Json
{
    /**
     * Thrown when text is not valid JSON.
     */
    public static class SyntaxException extends RuntimeException
    {
        /**
         * Creates an instance of SyntaxException.
         * @param msg A description of what was wrong.
         */
        public SyntaxException(String msg) { super(msg); }
    }

    /**
     * Not instantiable.
     */
    private Json() { }

    /* ---------------------------------------------------------------- *
     * Building
     * ---------------------------------------------------------------- */

    /**
     * Create an empty object, preserving the order keys are added in.
     * @return A new, empty JSON object.
     */
    public static Map<String, Object> object()
    {
        return new LinkedHashMap<String, Object>();
    }

    /**
     * Create an object from alternating keys and values.
     * @param pairs Key, value, key, value, and so on. Keys must be strings.
     * @return A new JSON object holding those pairs.
     */
    public static Map<String, Object> object(Object... pairs)
    {
        Map<String, Object> result = object();
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    /**
     * Create an array.
     * @param items The items to put in it.
     * @return A new JSON array holding those items.
     */
    public static List<Object> array(Object... items)
    {
        List<Object> result = new ArrayList<Object>();
        for (Object item : items)
        {
            result.add(item);
        }
        return result;
    }

    /* ---------------------------------------------------------------- *
     * Reading values back out
     * ---------------------------------------------------------------- */

    /**
     * Read a member of an object as an object.
     * @param value The object to look in; may be null.
     * @param key The member to read.
     * @return The member if it is an object, otherwise null.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object value, String key)
    {
        Object member = member(value, key);
        return member instanceof Map? (Map<String, Object>)member : null;
    }

    /**
     * Read a member of an object as an array.
     * @param value The object to look in; may be null.
     * @param key The member to read.
     * @return The member if it is an array, otherwise an empty list.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object value, String key)
    {
        Object member = member(value, key);
        return member instanceof List? (List<Object>)member : new ArrayList<Object>();
    }

    /**
     * Read a member of an object as a string.
     * @param value The object to look in; may be null.
     * @param key The member to read.
     * @param fallback What to return when the member is absent or null.
     * @return The member as a string, or the fallback.
     */
    public static String str(Object value, String key, String fallback)
    {
        Object member = member(value, key);
        return member == null? fallback : String.valueOf(member);
    }

    /**
     * Read a member of an object as a whole number.
     * @param value The object to look in; may be null.
     * @param key The member to read.
     * @param fallback What to return when the member is absent or not a number.
     * @return The member as a long, or the fallback.
     */
    public static long num(Object value, String key, long fallback)
    {
        Object member = member(value, key);
        if (member instanceof Number)
        {
            return ((Number)member).longValue();
        }
        if (member instanceof String) try
        {
            return Long.parseLong(((String)member).trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
        return fallback;
    }

    /**
     * Read a member of an object as a boolean.
     * @param value The object to look in; may be null.
     * @param key The member to read.
     * @param fallback What to return when the member is absent.
     * @return The member as a boolean, or the fallback.
     */
    public static boolean bool(Object value, String key, boolean fallback)
    {
        Object member = member(value, key);
        if (member instanceof Boolean)
        {
            return ((Boolean)member).booleanValue();
        }
        if (member instanceof String)
        {
            return Boolean.parseBoolean((String)member);
        }
        return fallback;
    }

    /**
     * Determine whether an object has a member at all, including one explicitly set to null.
     * @param value The object to look in; may be null.
     * @param key The member to look for.
     * @return true if the member is present.
     */
    public static boolean has(Object value, String key)
    {
        return value instanceof Map && ((Map<?, ?>)value).containsKey(key);
    }

    /**
     * Read a member of an object without interpreting it.
     * @param value The object to look in; may be null.
     * @param key The member to read.
     * @return The raw member, or null.
     */
    public static Object member(Object value, String key)
    {
        return value instanceof Map? ((Map<?, ?>)value).get(key) : null;
    }

    /* ---------------------------------------------------------------- *
     * Writing
     * ---------------------------------------------------------------- */

    /**
     * Render a value as JSON text.
     * @param value The value to render.
     * @return JSON text.
     */
    public static String write(Object value)
    {
        StringBuilder out = new StringBuilder();
        write(value, out, -1, 0);
        return out.toString();
    }

    /**
     * Render a value as indented JSON text.
     * @param value The value to render.
     * @param indent Spaces per level of nesting.
     * @return JSON text.
     */
    public static String writePretty(Object value, int indent)
    {
        StringBuilder out = new StringBuilder();
        write(value, out, indent, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int indent, int depth)
    {
        if (value == null)
        {
            out.append("null");
        }
        else if (value instanceof String || value instanceof Character)
        {
            writeString(String.valueOf(value), out);
        }
        else if (value instanceof Boolean)
        {
            out.append(value.toString());
        }
        else if (value instanceof Double || value instanceof Float)
        {
            double d = ((Number)value).doubleValue();
            // JSON has no way to say infinity or not-a-number, so say null rather than emit
            // something no reader will accept.
            if (Double.isNaN(d) || Double.isInfinite(d))
            {
                out.append("null");
            }
            else if (d == Math.rint(d) && Math.abs(d) < 1e15)
            {
                out.append((long)d);
            }
            else
            {
                out.append(Double.toString(d));
            }
        }
        else if (value instanceof Number)
        {
            out.append(value.toString());
        }
        else if (value instanceof Map)
        {
            writeMap((Map<?, ?>)value, out, indent, depth);
        }
        else if (value instanceof Iterable)
        {
            writeList((Iterable<?>)value, out, indent, depth);
        }
        else
        {
            writeString(String.valueOf(value), out);
        }
    }

    private static void writeMap(Map<?, ?> map, StringBuilder out, int indent, int depth)
    {
        if (map.isEmpty())
        {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet())
        {
            if (!first)
            {
                out.append(',');
            }
            first = false;
            newline(out, indent, depth + 1);
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            if (indent >= 0)
            {
                out.append(' ');
            }
            write(entry.getValue(), out, indent, depth + 1);
        }
        newline(out, indent, depth);
        out.append('}');
    }

    private static void writeList(Iterable<?> list, StringBuilder out, int indent, int depth)
    {
        if (!list.iterator().hasNext())
        {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (Object item : list)
        {
            if (!first)
            {
                out.append(',');
            }
            first = false;
            newline(out, indent, depth + 1);
            write(item, out, indent, depth + 1);
        }
        newline(out, indent, depth);
        out.append(']');
    }

    private static void newline(StringBuilder out, int indent, int depth)
    {
        if (indent < 0)
        {
            return;
        }
        out.append('\n');
        for (int i = 0; i < indent * depth; i++)
        {
            out.append(' ');
        }
    }

    private static void writeString(String s, StringBuilder out)
    {
        out.append('"');
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                default:
                    // Control characters must be escaped. Everything else, including the greek
                    // letters the machine model uses for lambda and epsilon, is written as-is; the
                    // stream is UTF-8 at both ends.
                    if (c < 0x20)
                    {
                        out.append(String.format("\\u%04x", (int)c));
                    }
                    else
                    {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    /* ---------------------------------------------------------------- *
     * Parsing
     * ---------------------------------------------------------------- */

    /**
     * Parse JSON text.
     * @param text The text to parse.
     * @return The value it describes.
     * @throws SyntaxException If the text is not valid JSON.
     */
    public static Object parse(String text)
    {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.value();
        p.skipWhitespace();
        if (p.pos < p.text.length())
        {
            throw new SyntaxException("unexpected text after the value, at offset " + p.pos);
        }
        return value;
    }

    /**
     * The recursive-descent parser behind {@link #parse}.
     */
    private static final class Parser
    {
        final String text;
        int pos = 0;

        Parser(String text) { this.text = text; }

        void skipWhitespace()
        {
            while (pos < text.length())
            {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
                {
                    pos++;
                }
                else
                {
                    return;
                }
            }
        }

        char peek()
        {
            if (pos >= text.length())
            {
                throw new SyntaxException("the text ended in the middle of a value");
            }
            return text.charAt(pos);
        }

        void expect(char c)
        {
            if (pos >= text.length() || text.charAt(pos) != c)
            {
                throw new SyntaxException(String.format(
                            "expected '%c' at offset %d but found %s", c, pos,
                            pos >= text.length()? "the end of the text" : "'" + text.charAt(pos) + "'"));
            }
            pos++;
        }

        Object value()
        {
            skipWhitespace();
            char c = peek();
            switch (c)
            {
                case '{': return objectValue();
                case '[': return arrayValue();
                case '"': return stringValue();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default:  return numberValue();
            }
        }

        Object literal(String word, Object result)
        {
            if (!text.startsWith(word, pos))
            {
                throw new SyntaxException("expected " + word + " at offset " + pos);
            }
            pos += word.length();
            return result;
        }

        Map<String, Object> objectValue()
        {
            Map<String, Object> result = object();
            expect('{');
            skipWhitespace();
            if (peek() == '}')
            {
                pos++;
                return result;
            }
            while (true)
            {
                skipWhitespace();
                String key = stringValue();
                skipWhitespace();
                expect(':');
                result.put(key, value());
                skipWhitespace();
                char c = peek();
                if (c == ',')
                {
                    pos++;
                }
                else if (c == '}')
                {
                    pos++;
                    return result;
                }
                else
                {
                    throw new SyntaxException("expected ',' or '}' at offset " + pos);
                }
            }
        }

        List<Object> arrayValue()
        {
            List<Object> result = new ArrayList<Object>();
            expect('[');
            skipWhitespace();
            if (peek() == ']')
            {
                pos++;
                return result;
            }
            while (true)
            {
                result.add(value());
                skipWhitespace();
                char c = peek();
                if (c == ',')
                {
                    pos++;
                }
                else if (c == ']')
                {
                    pos++;
                    return result;
                }
                else
                {
                    throw new SyntaxException("expected ',' or ']' at offset " + pos);
                }
            }
        }

        String stringValue()
        {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true)
            {
                if (pos >= text.length())
                {
                    throw new SyntaxException("the text ended in the middle of a string");
                }
                char c = text.charAt(pos++);
                if (c == '"')
                {
                    return sb.toString();
                }
                if (c != '\\')
                {
                    sb.append(c);
                    continue;
                }
                if (pos >= text.length())
                {
                    throw new SyntaxException("the text ended after a backslash");
                }
                char esc = text.charAt(pos++);
                switch (esc)
                {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length())
                        {
                            throw new SyntaxException("a \\u escape was cut short at offset " + pos);
                        }
                        sb.append((char)Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new SyntaxException("unknown escape \\" + esc + " at offset " + (pos - 1));
                }
            }
        }

        Object numberValue()
        {
            int start = pos;
            if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+'))
            {
                pos++;
            }
            boolean fractional = false;
            while (pos < text.length())
            {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9')
                {
                    pos++;
                }
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-')
                {
                    fractional = true;
                    pos++;
                }
                else
                {
                    break;
                }
            }
            String token = text.substring(start, pos);
            if (token.isEmpty() || token.equals("-") || token.equals("+"))
            {
                throw new SyntaxException("expected a value at offset " + start);
            }
            try
            {
                // Whole numbers stay whole: state counts and step budgets read better as 12 than
                // as 12.0, and a step budget large enough to matter does not survive a double.
                return fractional? (Object)Double.valueOf(token) : (Object)Long.valueOf(token);
            }
            catch (NumberFormatException e)
            {
                try
                {
                    return Double.valueOf(token);
                }
                catch (NumberFormatException e2)
                {
                    throw new SyntaxException("'" + token + "' at offset " + start + " is not a number");
                }
            }
        }
    }
}
