package rs.ac.bg.etf.kdp.tuple;

import java.io.*;
import java.util.*;
import java.util.stream.IntStream;

public class Tuple implements Serializable {

    protected final ArrayList<Serializable> fields;

    /**
     * Any Serializable objects are accepted.
     * Tuples that contain instances of Class<?>s
     * are considered template tuples and can be used
     * for matching
     */
    public Tuple(Serializable... elements) {
        this(new ArrayList<>(Arrays.asList(elements)));
    }

    protected Tuple(ArrayList<Serializable> fields) {
        this.fields = fields;
    }

    /**
     * Creates a tuple from a String[] by parsing each element.
     * Fields that start with ? are considered template fields
     * and question marks are to be followed by the value of
     * Class.getName()
     */
    public static Tuple valueOf(String[] fields) throws TupleFormatException {
        final var arr = new ArrayList<Serializable>(fields.length);
        for (int i = 0; i < fields.length; i++) {
            final var value = FieldParser.parse(fields[i]);
            if (value.isEmpty()) {
                throw new TupleFormatException("Invalid field format!");
            }
            arr.add(i,value.get());
        }
        return new Tuple(arr);
    }

    public String[] toStringArray() {
        return fields.stream()
                .map(Object::toString)
                .toArray(String[]::new);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (final var field : fields) {
            if (field instanceof Class<?>) {
                sb.append(" ?").append(((Class<?>) field).getName());
            } else if (field instanceof String) {
                sb.append(" \"").append(field).append('"');
            } else if (field instanceof Character) {
                sb.append(" '").append(field).append('\'');
            } else {
                sb.append(' ').append(field);
            }
            sb.append(',');
        }
        if (fields.size() != 0) {
            final int last = sb.length() - 1;
            sb.replace(last, last + 1, " ");
        }
        return sb.append(")").toString();
    }

    public static Tuple valueOf(String s) {
        final var st = new StringTokenizer(s, " ,");
        if (!st.hasMoreTokens() || !st.nextToken().equals("(")) {
            throw new TupleFormatException("Missing initial parenthesis");
        }
        Tuple result = valueOf(st);
        if (st.hasMoreTokens()) {
            throw new TupleFormatException("Excess characters after ')'");
        }
        return result;
    }

    private static Tuple valueOf(StringTokenizer st) {
        final var fields = new ArrayList<Serializable>();
        while (st.hasMoreTokens()) {
            final var token = st.nextToken().strip();
            if (token.equals(")")) return new Tuple(fields);
            if (Objects.equals(token, "(")) {
                fields.add(valueOf(st));
            }
            FieldParser.parse(token)
                    .ifPresentOrElse(fields::add,
                            () -> fields.add(token));
        }
        throw new TupleFormatException("Missing closing ')'");
    }

    public Serializable get(int i) throws IndexOutOfBoundsException {
        if (i < 0 || i > size()) {
            throw new IndexOutOfBoundsException();
        }
        return fields.get(i);
    }

    public int size() {
        return fields.size();
    }

    public boolean matches(Tuple template) {
        if (size() != template.size()) return false;
        return IntStream.range(0, size())
                .allMatch(i -> matches(get(i), template.get(i)));
    }

    private static boolean matches(
            Serializable valueField,
            Serializable templateField) {

        if (templateField.equals("null")) return true;
        if (templateField instanceof Tuple) {
            if (!(valueField instanceof Tuple)) return false;
            return ((Tuple) valueField).matches((Tuple) templateField);
        }
        if (templateField instanceof Class) {
            if (valueField instanceof Class) {
                return ((Class<?>) templateField).isAssignableFrom((Class<?>) valueField);
            }
            return ((Class<?>) templateField).isInstance(valueField);
        }
        return valueField.equals(templateField);
    }

    public Tuple deepCopy() throws IOException{
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;
        try {
            var bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            oos.flush();
            var bin = new ByteArrayInputStream(bos.toByteArray());
            ois = new ObjectInputStream(bin);
            return (Tuple) ois.readObject();
        } catch (ClassNotFoundException e) {
            return null;
        } finally {
            if (oos != null) oos.close();
            if (ois != null) ois.close();
        }
    }


    public static void main(String[] args) {
        var t1 = new Tuple( "hi", true, 78, Boolean.class);
        t1 = Tuple.valueOf(t1.toString());
        var t2 = Tuple.valueOf("( \"hi\" true null ?java.lang.Boolean )");
        var t3 = Tuple.valueOf(new String[]{"\"hi\"", "true", "78", "?Boolean"});

        System.out.println(t1);
        System.out.println(t2);
        System.out.println(t3);

        Tuple t4 = null;
        try{
            t4 = t1.deepCopy();
        }catch (IOException e){
            e.printStackTrace();
        }
        System.out.println(t4);
        System.out.println(Tuple.valueOf("( \"hi\", true, 78, false )").matches(t1));
    }
}
