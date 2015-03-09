package org.yamcs.protobuf;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.ByteString;

public class ValueHelper {
	
	/**
	 * returns a SINT64 Value
	 * @param l
	 */
	static public Value newValue(long l) {
		return Value.newBuilder().setType(Type.SINT64).setSint64Value(l).build();
	}
	/**
	 * returns a SINT32 Value
	 * @param x
	 * @return
	 */
	static public Value newValue(int x) {
		return Value.newBuilder().setType(Type.SINT32).setSint32Value(x).build();
	}
	
	/**
	 * returns a DOUBLE Value
	 * @param x
	 * @return
	 */
	static public Value newValue(Double x) {
		return Value.newBuilder().setType(Type.DOUBLE).setDoubleValue(x).build();
	}
	
	/**
	 * returns a FLOAT Value
	 * @param x
	 * @return
	 */
	static public Value newValue(Float x) {
		return Value.newBuilder().setType(Type.FLOAT).setFloatValue(x).build();
	}
	
	/**
	 * returns a STRING Value
	 * @param x
	 * @return
	 */
	static public Value newValue(String x) {
		return Value.newBuilder().setType(Type.STRING).setStringValue(x).build();
	}
	
	/**
	 * returns a BINARY Value
	 * @param x
	 * @return
	 */
	static public Value newValue(byte[] x) {
		return Value.newBuilder().setType(Type.BINARY).setBinaryValue(ByteString.copyFrom(x)).build();
	}
	
	
}