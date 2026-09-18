package me.copimine.worldcore.api;

public record WorldAccessResult(boolean success, boolean changed, String code, String message) {
}
