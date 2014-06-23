package com.ifesdjeen.cascading.cassandra.hadoop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.cassandra.db.marshal.AbstractCompositeType;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.CompositeType;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.cassandra.db.marshal.TypeParser;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.SyntaxException;
import org.codehaus.jackson.map.TypeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializerHelper {

	private static final Logger LOG = LoggerFactory
			.getLogger(SerializerHelper.class);

	public static Object deserialize(ByteBuffer bb, String type)
			throws SyntaxException, ConfigurationException {
		return deserialize(bb, inferType(type));
	}

	public static Object deserialize(ByteBuffer bb, AbstractType t)
			throws SyntaxException, ConfigurationException {

		if (t instanceof CompositeType) {
			CompositeType ct = (CompositeType) t;
			List<AbstractType<?>> componentTypes = ct.types;
			List<AbstractCompositeType.CompositeComponent> components = ct
					.deconstruct(bb);

			ArrayList objs = new ArrayList();
			for (int i = 0; i < componentTypes.size(); i++) {
				AbstractType componentType = componentTypes.get(i);
				Object obj = componentType.compose(components.get(i).value);
				objs.add(obj);
			}

			return objs;
		} else {
			if (bb.remaining() > 0) {
				return t.compose(bb);
			} else {
				return null;
			}
		}
	}

	public static AbstractType inferType(String t) {
		if (t == null) {
			throw new RuntimeException("can't infer type from 'null'");
		}
		try {
			return org.apache.cassandra.db.marshal.TypeParser.parse(t);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static ByteBuffer serialize(Object obj) {
		if (obj == null) {
			return nullToByteBuffer();
		} else if (obj instanceof BigInteger) {
			LOG.debug("Serializing {} as BigInteger.", obj);
			return bigIntegerToByteBuffer((BigInteger) obj);
		} else if (obj instanceof Boolean) {
			LOG.debug("Serializing {} as Boolean.", obj);
			return booleanToByteBuffer((Boolean) obj);
		} else if (obj instanceof Date) {
			LOG.debug("Serializing {} as Date.", obj);
			return dateToByteBuffer((Date) obj);
		} else if (obj instanceof Double) {
			LOG.debug("Serializing {} as Double.", obj);
			return doubleToByteBuffer((Double) obj);
		} else if (obj instanceof BigDecimal) {
			LOG.debug("Serializing {} as BigDecimal.", obj);
			return bigDecimalToByteBuffer((BigDecimal) obj);
		} else if (obj instanceof Float) {
			LOG.debug("Serializing {} as Float.", obj);
			return floatToByteBuffer((Float) obj);
		} else if (obj instanceof Integer) {
			LOG.debug("Serializing {} as Integer.", obj);
			return intToByteBuffer((Integer) obj);
		} else if (obj instanceof Long) {
			LOG.debug("Serializing {} as Long.", obj);
			return longToByteBuffer((Long) obj);
		} else if (obj instanceof Short) {
			LOG.debug("Serializing {} as Short.", obj);
			return shortToByteBuffer((Short) obj);
		} else if (obj instanceof String) {
			LOG.debug("Serializing {} as String.", obj);
			return stringToByteBuffer((String) obj);
		} else if (obj instanceof Set) {
			LOG.debug("Serializing {} as Set.", obj);
			return setToByteBuffer((Set) obj);
		}

		throw new RuntimeException("Could not serialize" + obj.toString()
				+ "\nJava reports type: " + obj.getClass().toString());
	}

	public static ByteBuffer serializeComposite(List components, CompositeType t) {

		CompositeType.Builder builder = new CompositeType.Builder(t);
		for (Object component : components) {
			ByteBuffer cbb = SerializerHelper.serialize(component);
			builder.add(cbb);
		}
		ByteBuffer r = builder.build();
		return r;
	}

	public static ByteBuffer nullToByteBuffer() {
		return ByteBuffer.wrap(new byte[0]);
	}

	public static ByteBuffer bigIntegerToByteBuffer(BigInteger obj) {
		return ByteBuffer.wrap(obj.toByteArray());
	}

	public static ByteBuffer bigDecimalToByteBuffer(BigDecimal value) {
		// copied from
		// https://github.com/apache/cassandra/blob/trunk/src/java/org/apache/cassandra/serializers/DecimalSerializer.java
		BigInteger bi = value.unscaledValue();
		int scale = value.scale();
		byte[] bibytes = bi.toByteArray();

		ByteBuffer bytes = ByteBuffer.allocate(4 + bibytes.length);
		bytes.putInt(scale);
		bytes.put(bibytes);
		bytes.rewind();
		return bytes;
	}

	public static ByteBuffer booleanToByteBuffer(Boolean obj) {
		boolean bool = obj;
		byte[] b = new byte[1];
		b[0] = bool ? (byte) 1 : (byte) 0;

		return ByteBuffer.wrap(b);
	}

	public static ByteBuffer dateToByteBuffer(Date obj) {
		return longToByteBuffer(obj.getTime());
	}

	public static ByteBuffer longToByteBuffer(Long obj) {
		return ByteBuffer.allocate(8).putLong(0, obj);
	}

	public static ByteBuffer doubleToByteBuffer(Double obj) {
		return ByteBuffer.allocate(8).putDouble(0, obj);
	}

	public static ByteBuffer floatToByteBuffer(Float obj) {
		return intToByteBuffer(Float.floatToRawIntBits(obj));
	}

	public static ByteBuffer intToByteBuffer(Integer obj) {
		return ByteBuffer.allocate(4).putInt(0, obj);
	}

	public static ByteBuffer shortToByteBuffer(Short obj) {
		ByteBuffer b = ByteBuffer.allocate(2);
		b.putShort(obj);
		b.rewind();
		return b;
	}

	public static ByteBuffer stringToByteBuffer(String obj) {
		return ByteBuffer.wrap(obj.getBytes());
	}

	public static ByteBuffer setToByteBuffer(Set obj) {
		ByteBuffer bb = null;
		try {
			SetType serializer = SetType.getInstance(new TypeParser(
					"(UTF8Type)"));
			Method method = null;
			try {
				method = serializer.getClass().getMethod("getSerializer");
				Object tSerializer = method.invoke(
						serializer);
				bb = (ByteBuffer) tSerializer.getClass()
						.getMethod("serialize", Object.class).invoke(tSerializer, obj);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException("Could not serialize Set", e);
			} catch (SecurityException e) {
				throw new RuntimeException("Could not serialize Set", e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Could not serialize Set", e);
			} catch (InvocationTargetException e) {
				throw new RuntimeException("Could not serialize Set", e);
			}
		} catch (ConfigurationException e) {
			throw new RuntimeException("Could not serialize Set", e);
		} catch (SyntaxException e) {
			throw new RuntimeException("Could not serialize Set", e);
		}
		return bb;
	}
}
