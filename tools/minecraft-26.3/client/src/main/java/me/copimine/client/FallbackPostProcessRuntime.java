package me.copimine.client;

public final class FallbackPostProcessRuntime {
    private final ClientPostProcessController controller;

    public FallbackPostProcessRuntime(ClientPostProcessController controller) {
        this.controller = controller;
    }

    public boolean apply(ShaderEffectRequest request) {
        return controller.apply(request.effectId(), request.intensity());
    }

    public void clear() {
        controller.clear();
    }

    public String statusLine() {
        return controller.statusLine();
    }

    public String lastFailureReason() {
        return controller.lastFailureReason();
    }
}
