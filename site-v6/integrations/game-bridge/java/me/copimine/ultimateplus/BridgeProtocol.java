package me.copimine.ultimateplus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Strict JSON, signed spool records and durable replacement; no server dependency. */
public final class BridgeProtocol {
    private BridgeProtocol() {}
    public record Envelope(byte[] body, Map<String,Object> value) {}
    public static final int MAX_INPUT=2*1024*1024, MAX_RECORD=32*1024*1024;
    public static Map<String,Object> map(Object value) {
        if(!(value instanceof Map<?,?> raw)) throw new IllegalArgumentException("object_required");
        LinkedHashMap<String,Object> result=new LinkedHashMap<>();
        for(var item:raw.entrySet()) {
            if(!(item.getKey() instanceof String key)) throw new IllegalArgumentException("string_key_required");
            result.put(key,item.getValue());
        }
        return result;
    }
    public static Map<String,Object> object(Object... entries) {
        LinkedHashMap<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<entries.length;i+=2)result.put((String)entries[i],entries[i+1]);
        return result;
    }
    public static String string(Map<String,Object> obj,String key,int max) {
        Object value=obj.get(key);
        if(!(value instanceof String s)||s.isEmpty()||s.length()>max)throw new IllegalArgumentException("invalid_"+key);
        return s;
    }
    public static String optional(Map<String,Object> obj,String key,String fallback) {
        Object value=obj.get(key);
        if(value==null)return fallback;
        if(!(value instanceof String s)||s.length()>1000)throw new IllegalArgumentException("invalid_"+key);
        return s;
    }
    public static long number(Map<String,Object> obj,String key,long min,long max) {
        Object value=obj.get(key);
        if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue()||n.longValue()<min||n.longValue()>max)
            throw new IllegalArgumentException("invalid_"+key);
        return n.longValue();
    }
    public static double decimal(Map<String,Object> obj,String key,double min,double max,double fallback) {
        Object value=obj.get(key);
        if(value==null)return fallback;
        if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)
            throw new IllegalArgumentException("invalid_"+key);
        return n.doubleValue();
    }
    public static boolean bool(Map<String,Object> obj,String key) {
        Object value=obj.get(key);
        if(!(value instanceof Boolean b))throw new IllegalArgumentException("invalid_"+key);
        return b;
    }
    public static UUID uuid(String value) {
        UUID id=UUID.fromString(value);
        if(!id.toString().equals(value))throw new IllegalArgumentException("canonical_uuid_required");
        return id;
    }
    public static byte[] read(Path path,int max)throws IOException {
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>max)
            throw new IOException("invalid_spool_file");
        byte[] bytes=Files.readAllBytes(path);
        if(bytes.length>max)throw new IOException("oversized_spool_file");
        return bytes;
    }
    public static byte[] secret(Path path)throws IOException {
        String hex=new String(read(path,1024),StandardCharsets.US_ASCII).trim();
        if(!hex.matches("[0-9a-fA-F]{64}"))throw new IOException("invalid_spool_secret");
        return HexFormat.of().parseHex(hex);
    }
    public static String sha(byte[] body) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String hmac(byte[] secret,byte[] body) {
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(body));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static Envelope open(byte[] secret,byte[] record) {
        Map<String,Object> outer=map(parse(record));
        byte[] body=Base64.getDecoder().decode(string(outer,"payload",MAX_RECORD));
        String signature=string(outer,"signature",64);
        if(!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),hmac(secret,body).getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("spool_authentication_failed");
        return new Envelope(body,map(parse(body)));
    }
    public static byte[] signed(byte[] secret,Object value) {
        byte[] body=json(value);
        return json(object("payload",Base64.getEncoder().encodeToString(body),"signature",hmac(secret,body)));
    }
    public static void atomic(Path path,byte[] body)throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".tmp");
        try {
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(body);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            try {Files.setPosixFilePermissions(temporary,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}
            catch(UnsupportedOperationException ignored){}
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)){directory.force(true);}
            catch(IOException|UnsupportedOperationException ignored){}
        } finally {Files.deleteIfExists(temporary);}
    }
    public static byte[] json(Object value) {
        StringBuilder text=new StringBuilder();
        encode(value,text,0);
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void encode(Object value,StringBuilder out,int depth) {
        if(depth>64)throw new IllegalArgumentException("json_too_deep");
        if(value==null){out.append("null");return;}
        if(value instanceof String s){quote(s,out);return;}
        if(value instanceof Boolean b){out.append(b);return;}
        if(value instanceof Number n){if(!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("nonfinite_number");out.append(n);return;}
        if(value instanceof Map<?,?> raw){
            out.append('{');boolean first=true;
            for(var entry:new TreeMap<>(map(raw)).entrySet()){
                if(!first)out.append(',');first=false;quote(entry.getKey(),out);out.append(':');encode(entry.getValue(),out,depth+1);
            }
            out.append('}');return;
        }
        if(value instanceof Collection<?> list){
            out.append('[');boolean first=true;
            for(Object item:list){if(!first)out.append(',');first=false;encode(item,out,depth+1);}
            out.append(']');return;
        }
        throw new IllegalArgumentException("unsupported_json_value");
    }
    private static void quote(String value,StringBuilder out) {
        out.append('"');
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '"'->out.append("\\\"");case '\\'->out.append("\\\\");
                case '\b'->out.append("\\b");case '\f'->out.append("\\f");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");
                default->{if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
            }
        }
        out.append('"');
    }
    public static Object parse(byte[] body) {
        if(body.length>MAX_RECORD)throw new IllegalArgumentException("json_too_large");
        return new Parser(new String(body,StandardCharsets.UTF_8)).parse();
    }
    private static final class Parser {
        final String text;int index=0;
        Parser(String value){text=value;}
        Object parse(){Object value=value(0);space();if(index!=text.length())throw invalid();return value;}
        IllegalArgumentException invalid(){return new IllegalArgumentException("invalid_json");}
        void space(){while(index<text.length()&&" \n\r\t".indexOf(text.charAt(index))>=0)index++;}
        char next(){if(index>=text.length())throw invalid();return text.charAt(index++);}
        Object value(int depth){
            if(depth>64)throw invalid();space();if(index>=text.length())throw invalid();char c=text.charAt(index);
            if(c=='"')return string();
            if(c=='{'){
                index++;space();Map<String,Object> out=new LinkedHashMap<>();
                if(index<text.length()&&text.charAt(index)=='}'){index++;return out;}
                while(true){space();String key=string();space();if(next()!=':')throw invalid();if(out.containsKey(key))throw invalid();out.put(key,value(depth+1));space();char end=next();if(end=='}')return out;if(end!=',')throw invalid();}
            }
            if(c=='['){
                index++;space();List<Object> out=new ArrayList<>();
                if(index<text.length()&&text.charAt(index)==']'){index++;return out;}
                while(true){out.add(value(depth+1));space();char end=next();if(end==']')return out;if(end!=',')throw invalid();}
            }
            for(String literal:List.of("true","false","null"))if(text.startsWith(literal,index)){index+=literal.length();return literal.equals("null")?null:literal.equals("true");}
            int start=index;
            while(index<text.length()&&"-+0123456789.eE".indexOf(text.charAt(index))>=0)index++;
            String number=text.substring(start,index);
            if(!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw invalid();
            try {if(!number.contains(".")&&!number.contains("e")&&!number.contains("E"))return Long.parseLong(number);
                double parsed=Double.parseDouble(number);if(!Double.isFinite(parsed))throw invalid();return parsed;}
            catch(NumberFormatException error){throw invalid();}
        }
        String string(){
            if(next()!='"')throw invalid();StringBuilder out=new StringBuilder();
            while(true){
                char c=next();if(c=='"')return out.toString();if(c<32)throw invalid();
                if(c!='\\'){out.append(c);continue;}
                char escape=next();
                switch(escape){
                    case '"','\\','/'->out.append(escape);case 'b'->out.append('\b');case 'f'->out.append('\f');case 'n'->out.append('\n');case 'r'->out.append('\r');case 't'->out.append('\t');
                    case 'u'->{if(index+4>text.length())throw invalid();try{out.append((char)Integer.parseInt(text.substring(index,index+4),16));index+=4;}catch(NumberFormatException error){throw invalid();}}
                    default->throw invalid();
                }
            }
        }
    }
}

