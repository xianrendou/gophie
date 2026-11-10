package org.gophie.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

/** Routes the usual mouse side buttons to history throughout one window. */
final class HistoryMouseListener implements AWTEventListener {
    private final Component root;
    private final Runnable backward;
    private final Runnable forward;

    HistoryMouseListener(Component root, Runnable backward, Runnable forward) {
        this.root = root;
        this.backward = backward;
        this.forward = forward;
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (!(event instanceof MouseEvent)) {
            return;
        }
        MouseEvent mouse = (MouseEvent) event;
        if (mouse.isConsumed() || !(mouse.getSource() instanceof Component)) {
            return;
        }
        Component source = (Component) mouse.getSource();
        if (source != root && !SwingUtilities.isDescendingFrom(source, root)) {
            return;
        }
        // Java numbers extra buttons from 4; the usual Back/Forward pair is 4/5.
        if (mouse.getButton() != 4 && mouse.getButton() != 5) {
            return;
        }
        // Consume the whole gesture so it cannot also activate a control.
        mouse.consume();
        // Navigate once per press/release/click sequence, including double clicks.
        if (mouse.getID() == MouseEvent.MOUSE_RELEASED) {
            if (mouse.getButton() == 4) {
                backward.run();
            } else {
                forward.run();
            }
        }
    }
}
