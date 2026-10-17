import me.copimine.ultimateplus.BridgeProtocol;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Pure JVM contract checks. Never loads Bukkit or connects to a game server. */
public final class BridgeProtocolCheck {
    private static int checks;
    private static void check(boolean value) {if(!value)throw new AssertionError();checks++;}
    private static void rejects(Runnable operation) {
        try {operation.run();}catch(IllegalArgumentException expected){checks++;return;}
        throw new AssertionError("Malformed protocol data was accepted");
    }
    public static void main(String[] args)throws Exception {
        byte[] secret=new byte[32];Arrays.fill(secret,(byte)1); // Public test fixture only.
        byte[] input=BridgeProtocol.read(Path.of(args[0]),BridgeProtocol.MAX_INPUT);
        var decoded=BridgeProtocol.open(secret,input);
        check(BridgeProtocol.sha(decoded.body()).equals(args[2]));
        check(decoded.value().get("text").equals("Проверка 🐉\nточной подписи"));
        check(BridgeProtocol.number(decoded.value(),"capturedAt",0,Long.MAX_VALUE)==1792238400123L);
        check(BridgeProtocol.uuid((String)decoded.value().get("operationId")).toString().equals("8e1a597b-f861-4c73-b8d5-37c47ac60fe8"));
        rejects(()->BridgeProtocol.parse("{\"x\":1,\"x\":2}".getBytes(StandardCharsets.UTF_8)));
        rejects(()->BridgeProtocol.parse("{\"x\":NaN}".getBytes(StandardCharsets.UTF_8)));
        rejects(()->BridgeProtocol.parse("[01]".getBytes(StandardCharsets.UTF_8)));
        rejects(()->BridgeProtocol.parse("[1e999]".getBytes(StandardCharsets.UTF_8)));
        rejects(()->BridgeProtocol.parse(("[".repeat(66)+"0"+"]".repeat(66)).getBytes(StandardCharsets.UTF_8)));
        rejects(()->BridgeProtocol.uuid("1-1-1-1-1"));
        Map<String,Object> forged=BridgeProtocol.map(BridgeProtocol.parse(input));
        forged.put("signature","0".repeat(64));
        rejects(()->BridgeProtocol.open(secret,BridgeProtocol.json(forged)));
        byte[] signed=BridgeProtocol.signed(secret,decoded.value());
        check(BridgeProtocol.open(secret,signed).value().equals(decoded.value()));
        Path output=Path.of(args[1]);
        BridgeProtocol.atomic(output,"previous".getBytes(StandardCharsets.UTF_8));
        BridgeProtocol.atomic(output,signed);
        check(Arrays.equals(BridgeProtocol.read(output,BridgeProtocol.MAX_RECORD),signed));
        System.out.println(new String(BridgeProtocol.json(BridgeProtocol.object("passed",true,"checks",checks,"gameServerStarted",false)),StandardCharsets.UTF_8));
    }
}
