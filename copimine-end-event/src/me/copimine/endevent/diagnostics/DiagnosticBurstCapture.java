package me.copimine.endevent.diagnostics;

/** Bounded pre/post history around an anomaly. */
public interface DiagnosticBurstCapture {
    void sample(EndRiftDiagnosticEvent event);

    void trigger(String correlationId, String reason);
}
