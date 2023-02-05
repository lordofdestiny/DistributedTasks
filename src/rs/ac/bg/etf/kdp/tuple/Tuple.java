package rs.ac.bg.etf.kdp.tuple;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.stream.IntStream;

public class Tuple implements Serializable {
	final static long serialVersionUID = 0L;
	protected final ArrayList<Serializable> fields;

	/**
	 * Any Serializable objects are accepted. Tuples that contain instances of
	 * Class<?>s are considered template tuples and can be used for matching
	 */

	protected Tuple(ArrayList<Serializable> fields) {
		this.fields = fields;
	}

	/**
	 * Creates a tuple from a String[] by parsing each element. Fields that start
	 * with ? are considered template fields and question marks are to be followed
	 * by the value of Class.getName()
	 */
	public Tuple(String[] tuple) {
		fields = new ArrayList<>(tuple.length);
		for (final var field : tuple) {
			final var value = FieldParser.parse(field);
			if (value.isEmpty()) {
				throw new TupleFormatException("Invalid field format!");
			}
			fields.add(value.get());
		}
	}

	public String[] toStringArray() {
		return fields.stream().map(Object::toString).toArray(String[]::new);
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

	public Serializable get(int i) {
		return fields.get(i);
	}

	public int size() {
		return fields.size();
	}

	public boolean matches(Tuple template) {
		if (size() != template.size())
			return false;
		return IntStream.range(0, size()).allMatch(i -> matches(get(i), template.get(i)));
	}

	private static boolean matches(Serializable valueField, Serializable templateField) {

		if (templateField.equals("null"))
			return true;
		if (templateField instanceof Tuple) {
			if (!(valueField instanceof Tuple))
				return false;
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
}
