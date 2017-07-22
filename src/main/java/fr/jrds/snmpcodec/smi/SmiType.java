package fr.jrds.snmpcodec.smi;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snmp4j.asn1.BER;
import org.snmp4j.asn1.BERInputStream;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.Opaque;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.Utils;
import fr.jrds.snmpcodec.log.LogAdapter;

/**
 * A enumeration of Snmp types to help conversion and parsing.
 * @author Fabrice Bacchella
 *
 */
public enum SmiType implements Codec {

    /**
     * From SNMPv2-SMI, defined as [APPLICATION 4]<p>
     * -- for backward-compatibility only<p/>
     * This can also manage the special float type as defined by Net-SNMP. But it don't parse float.<p>
     * @author Fabrice Bacchella
     */
    Opaque {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Opaque();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof byte[])) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            byte[] a = (byte[]) source;
            return new org.snmp4j.smi.Opaque(a);
        }
        @Override
        public Object convert(Variable v) {
            Opaque var = (Opaque) v;
            //If not resolved, we will return the data as an array of bytes
            Object value = var.getValue();
            try {
                byte[] bytesArray = var.getValue();
                ByteBuffer bais = ByteBuffer.wrap(bytesArray);
                BERInputStream beris = new BERInputStream(bais);
                byte t1 = bais.get();
                byte t2 = bais.get();
                int l = BER.decodeLength(beris);
                if(t1 == TAG1) {
                    if(t2 == TAG_FLOAT && l == 4)
                        value = new Float(bais.getFloat());
                    else if(t2 == TAG_DOUBLE && l == 8)
                        value = new Double(bais.getDouble());
                }
            } catch (IOException e) {
                logger.error(var.toString());
            }
            return value;
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Opaque(text.getBytes());
        }
    },
    OctetString {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.OctetString();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof byte[])) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            byte[] a = (byte[]) source;
            return new org.snmp4j.smi.OctetString(a);
        }
        @Override
        public Object convert(Variable v) {
            org.snmp4j.smi.OctetString octetVar = (org.snmp4j.smi.OctetString)v;
            //It might be a C string, try to remove the last 0;
            //But only if the new string is printable
            int length = octetVar.length();
            if(length > 1 && octetVar.get(length - 1) == 0) {
                org.snmp4j.smi.OctetString newVar = octetVar.substring(0, length - 1);
                if(newVar.isPrintable()) {
                    v = newVar;
                    logger.debug("Convertion an octet stream from %s to %s", octetVar, v);
                }
            }
            return v.toString();
        }
        @Override
        public String format(Variable v) {
            return v.toString();
        }
        @Override
        public Variable parse(String text) {
            return org.snmp4j.smi.OctetString.fromByteArray(text.getBytes());
        }
    },
    /**
     * From SNMPv2-SMI, defined as [APPLICATION 2]<p>
     * -- an unsigned 32-bit quantity<p/>
     * -- indistinguishable from Gauge32
     * @author Fabrice Bacchella
     *
     */
    Unsigned32 {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.UnsignedInteger32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.UnsignedInteger32(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toLong();
        }
    },
    /**
     * @deprecated
     *    The BIT STRING type has been temporarily defined in RFC 1442
     *    and obsoleted by RFC 2578. Use OctetString (i.e. BITS syntax)
     *    instead.
     */
    BitString {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.BitString();
        }
        @Override
        public Variable getVariable(Object source) {
            return null;
        }
        @Override
        public Object convert(Variable v) {
            return v.toString();
        }
    },
    /**
     * From SNMPv-2SMI, defined as [APPLICATION 0]<p>
     * -- (this is a tagged type for historical reasons)
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.IpAddress} variable.</li>
     * <li>{@link #convert(Variable)} return a {@link java.net.InetAddress}.</li>
     * <li>{@link #format(OidInfos, Variable)} try to resolve the hostname associated with the IP address.</li>
     * <li>{@link #parse(OidInfos, String)} parse the string as an hostname or a IP address.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    IpAddr {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.IpAddress();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof InetAddress) && ! (source instanceof String)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            if (source instanceof InetAddress) {
                return new org.snmp4j.smi.IpAddress((InetAddress) source);
            } else {
                return new org.snmp4j.smi.IpAddress((String) source);
            }
        }
        @Override
        public Object convert(Variable v) {
            return ((IpAddress)v).getInetAddress();
        }
        @Override
        public String format(Variable v) {
            IpAddress ip = (IpAddress) v;
            return ip.getInetAddress().getHostName();
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.IpAddress(text);
        }
    },
    ObjID {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.OID();
        }
        @Override
        public Variable getVariable(Object source) {
            if (source instanceof int[]) {
                int[] oid = (int[]) source;
                return new org.snmp4j.smi.OID(oid);
            } else if(source instanceof String) {
                return new org.snmp4j.smi.OID((String)source);
            } else {
                throw new IllegalArgumentException("Given a variable of type  instead of OID");
            }
        }
        @Override
        public Object convert(Variable v) {
            return v;
        }
        @Override
        public String format(Variable v) {
            return ((OID)v).format();
        }
        @Override
        public Variable parse(String text) {
            return new OID(text);
        }
    },
    INTEGER {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Integer32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Integer32(n.intValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toInt();
        }
        @Override
        public String format(Variable v) {
            return java.lang.String.valueOf(v.toInt());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Integer32(Integer.parseInt(text));
        }
    },
    /**
     * From SNMPv2-SMI, defined as [APPLICATION 1]<p>
     * -- this wraps <p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.Counter32} variable.</li>
     * <li>{@link #convert(Variable)} return the value stored in a {@link java.lang.Long}.</li>
     * <li>{@link #parse(OidInfos, String)} parse the string as a long value.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    Counter32 {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Counter32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Counter32(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toLong();
        }
        @Override
        public String format(Variable v) {
            return java.lang.String.valueOf(v.toLong());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Counter32(Long.parseLong(text));
        }
    },
    /**
     * From SNMPv2-SMI, defined as [APPLICATION 6]<p>
     * -- for counters that wrap in less than one hour with only 32 bits</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.Counter64} variable.</li>
     * <li>{@link #convert(Variable)} return the value stored in a {@link fr.jrds.SmiExtensions.Utils.UnsignedLong}.</li>
     * <li>{@link #parse(OidInfos, String)} parse the string as a long value.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    Counter64 {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Counter64();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Counter64(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return Utils.getUnsigned(v.toLong());
        }
        @Override
        public String format(Variable v) {
            return Long.toUnsignedString(v.toLong());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Counter64(Long.parseLong(text));
        }
    },
    /**
     * From SNMPv-2SMI, defined as [APPLICATION 2]<p>
     * -- this doesn't wrap</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.Gauge32} variable.</li>
     * <li>{@link #convert(Variable)} return the value stored in a Long.</li>
     * <li>{@link #parse(OidInfos, String)} parse the string as a long value.</li>
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    Gauge32 {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Gauge32();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new org.snmp4j.smi.Gauge32(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return v.toLong();
        }
        @Override
        public String format(Variable v) {
            return java.lang.String.valueOf(v.toLong());
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Gauge32(Long.parseLong(text));
        }
    },
    /**
     * From SNMPv-2SMI, defined as [APPLICATION 3]<p>
     * -- hundredths of seconds since an epoch</p>
     * <ul>
     * <li>{@link #getVariable()} return an empty {@link org.snmp4j.smi.TimeTicks} variable.</li>
     * <li>{@link #convert(Variable)} return the time ticks as a number of milliseconds stored in a Long</li>
     * <li>{@link #format(OidInfos, Variable)} format the value using {@link org.snmp4j.smi.TimeTicks#toString()}
     * <li>{@link #parse(OidInfos, String)} can parse a number, expressing timeticks or the result of {@link org.snmp4j.smi.TimeTicks#toString()}
     * </ul>
     * @author Fabrice Bacchella
     *
     */
    TimeTicks {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.TimeTicks();
        }
        @Override
        public Variable getVariable(Object source) {
            if (! (source instanceof Number)) {
                throw new IllegalArgumentException("Given a variable of type  instead of type OCTET STRING");
            }
            Number n = (Number) source;
            return new TimeTicks(n.longValue());
        }
        @Override
        public Object convert(Variable v) {
            return ((TimeTicks)v).toMilliseconds();
        }
        @Override
        public String format(Variable v) {
            return v.toString();
        }
        @Override
        public Variable parse(String text) {
            try {
                long duration = Long.parseLong(text);
                return new org.snmp4j.smi.TimeTicks(duration);
            } catch (NumberFormatException e) {
                Matcher m = TimeTicksPattern.matcher(text);
                if (m.matches()) {
                    String days = m.group("days") != null ? m.group("days") : "0";
                    String hours = m.group("hours");
                    String minutes = m.group("minutes");
                    String seconds = m.group("seconds");
                    String fraction = m.group("fraction");
                    String formatted = java.lang.String.format("P%sDT%sH%sM%s.%sS", days, hours, minutes,seconds, fraction);
                    TimeTicks tt = new TimeTicks();
                    tt.fromMilliseconds(Duration.parse(formatted).toMillis());
                    return tt;
                } else {
                    return new org.snmp4j.smi.Null();
                }
            }
        }
    },
    Null {
        @Override
        public Variable getVariable() {
            return new org.snmp4j.smi.Null();
        }
        @Override
        public Variable getVariable(Object source) {
            return getVariable();
        }
        @Override
        public Object convert(Variable v) {
            return null;
        }
        @Override
        public String format(Variable v) {
            return "";
        }
        @Override
        public Variable parse(String text) {
            return new org.snmp4j.smi.Null();
        }
    },
    ;

    // Used to parse time ticks
    static final private Pattern TimeTicksPattern = Pattern.compile("(?:(?<days>\\d+) days?, )?(?<hours>\\d+):(?<minutes>\\d+):(?<seconds>\\d+)(?:\\.(?<fraction>\\d+))?");

    static final private LogAdapter logger = LogAdapter.getLogger(SmiType.class);

    static final private byte TAG1 = (byte) 0x9f;
    static final private byte TAG_FLOAT = (byte) 0x78;
    static final private byte TAG_DOUBLE = (byte) 0x79;

    /**
     * @return a empty instance of the associated Variable type
     */
    public abstract Variable getVariable();
    public abstract Variable getVariable(Object source);
    public String format(Variable v) {
        return v.toString();
    };
    public Variable parse(String text) {
        return null;
    };
    public abstract Object convert(Variable v);
    public Object make(int[] in){
        Variable v = getVariable();
        OID oid = new OID(in);
        v.fromSubIndex(oid, true);
        return convert(v);
    }
    @Override
    public Constraint getConstrains() {
        return null;
    };

}
