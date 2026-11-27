package io.github.hiveko.keycaps.screen;

/** Stands in for Minecraft's KeyEvent (1.21.9 and newer) in the code copied from 26.x (see mc1.21.1/build.gradle). */
public record KeyEvent(int key, int scancode, int modifiers) {
}
