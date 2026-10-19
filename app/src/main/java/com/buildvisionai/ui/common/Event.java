package com.buildvisionai.ui.common;

/**
 * A value delivered once through LiveData (a navigation request, a message). After the first call
 * to {@link #getIfNotHandled()} it returns null, so a rotation or a re-subscribe does not repeat it.
 */
public final class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) { this.content = content; }

    /** The content the first time, null afterwards. */
    public T getIfNotHandled() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() { return content; }
}
