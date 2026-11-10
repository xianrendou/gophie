package org.gophie.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.gophie.ui.event.NavigationInputListener;

/** Exercises real component dispatch without requiring a physical five-button mouse. */
public class HistoryMouseListenerTest {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("gophie-mouse-test-");
        System.setProperty("user.home", home.toString());
        try {
            SwingUtilities.invokeAndWait(HistoryMouseListenerTest::runTests);
            System.out.println("All mouse history tests passed.");
        } finally {
            Files.deleteIfExists(home.resolve("Gophie"));
            Files.deleteIfExists(home);
        }
    }

    private static void runTests() {
        int[] moves = new int[2];
        JPanel root = new JPanel();
        JPanel page = new JPanel();
        JTextField address = new JTextField();
        root.add(page);
        root.add(address);
        HistoryMouseListener listener = new HistoryMouseListener(root,
            () -> moves[0]++, () -> moves[1]++);
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
        try {
            gesture(page, 4);
            equal(1, moves[0], "Back navigates once over page");
            gesture(address, 5);
            equal(1, moves[1], "Forward works over address field");
            gesture(root, 4);
            equal(2, moves[0], "window background works");

            for (int button = 1; button <= 3; button++) {
                MouseEvent ordinary = event(page, MouseEvent.MOUSE_RELEASED, button, 1);
                page.dispatchEvent(ordinary);
                check(!ordinary.isConsumed(), "ordinary button remains available: " + button);
            }
            gesture(new JPanel(), 4);
            gesture(new JPanel(), 5);
            equal(2, moves[0], "another window does not navigate Back");
            equal(1, moves[1], "another window does not navigate Forward");
            MouseEvent consumed = event(page, MouseEvent.MOUSE_RELEASED, 4, 1);
            consumed.consume();
            listener.eventDispatched(consumed);
            listener.eventDispatched(new ActionEvent(page, ActionEvent.ACTION_PERFORMED, "ignore"));
            equal(2, moves[0], "consumed and unrelated events ignored");
            MouseEvent secondClick = event(page, MouseEvent.MOUSE_RELEASED, 4, 2);
            page.dispatchEvent(secondClick);
            equal(3, moves[0], "each release in a double click navigates once");

            // The existing controls must honor consumption, otherwise a side button
            // over Refresh, Home, a history arrow, or a confirmation also activates it.
            NavigationBar bar = new NavigationBar("#248AC2", "#76bce3", "#ffffff");
            int[] controlActions = new int[1];
            bar.addListener((NavigationInputListener) Proxy.newProxyInstance(
                NavigationInputListener.class.getClassLoader(),
                new Class<?>[] {NavigationInputListener.class}, (proxy, method, args) -> {
                    controlActions[0]++;
                    return null;
                }));
            bar.setNavigateBack(true);
            bar.setNavigateForward(true);
            root.add(bar);
            checkLabels(bar, controlActions);
            MessageView message = new MessageView();
            root.add(message);
            message.showConfirm("Test", new String[] {"Confirm"}, option -> controlActions[0]++);
            checkLabels(message, controlActions);
            message.showInfo("Test");
            sideButtonsOnLabels(message);
            check(message.isVisible(), "side buttons do not dismiss an information message");
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
        int before = moves[0];
        gesture(page, 4);
        equal(before, moves[0], "listener removed on cleanup");
    }

    private static void checkLabels(Container container, int[] actions) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel && child.getMouseListeners().length > 0) {
                int before = actions[0];
                gesture(child, 4);
                gesture(child, 5);
                equal(before, actions[0], "side buttons do not activate " + ((JLabel) child).getText());
                child.dispatchEvent(event(child, MouseEvent.MOUSE_RELEASED, 1, 1));
                equal(before + 1, actions[0], "left click still activates " + ((JLabel) child).getText());
            } else if (child instanceof Container) {
                checkLabels((Container) child, actions);
            }
        }
    }

    private static void sideButtonsOnLabels(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel) {
                gesture(child, 4);
                gesture(child, 5);
            } else if (child instanceof Container) {
                sideButtonsOnLabels((Container) child);
            }
        }
    }

    private static void gesture(Component source, int button) {
        for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                                MouseEvent.MOUSE_CLICKED}) {
            source.dispatchEvent(event(source, id, button, 1));
        }
    }

    private static MouseEvent event(Component source, int id, int button, int clicks) {
        // Override the button so headless CI can exercise extra-button handling
        // even when its Toolkit reports only a three-button mouse.
        return new MouseEvent(source, id, 0, 0, 1, 1, 1, 1, clicks, false, MouseEvent.BUTTON1) {
            @Override public int getButton() { return button; }
        };
    }

    private static void equal(int expected, int actual, String message) {
        check(expected == actual, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
