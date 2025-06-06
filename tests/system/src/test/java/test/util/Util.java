/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package test.util;

import static org.junit.jupiter.api.Assertions.fail;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.junit.jupiter.api.Assertions;
import com.sun.javafx.PlatformUtil;

/**
 * Utility methods for life-cycle testing
 */
public class Util {
    /** Default startup timeout value in seconds */
    public static final int STARTUP_TIMEOUT = 15;
    /** Test timeout value in milliseconds */
    public static final int TIMEOUT = 10000;

    private static interface Future {
        public abstract boolean await(long timeout, TimeUnit unit);
    }

    public static void throwError(Throwable testError) {
        if (testError != null) {
            if (testError instanceof Error) {
                throw (Error)testError;
            } else if (testError instanceof RuntimeException) {
                throw (RuntimeException)testError;
            } else {
                fail(testError);
            }
        } else {
            fail("Unexpected exception");
        }
    }

    public static void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException ex) {
            fail(ex);
        }
    }

    public static boolean await(final CountDownLatch latch) {
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Future submit(final Runnable r, final CountDownLatch delayLatch) {
        final Throwable[] testError = new Throwable[1];
        final CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                if (delayLatch != null) {
                    delayLatch.await();
                }
                r.run();
            } catch (Throwable th) {
                testError[0] = th;
            } finally {
                latch.countDown();
            }
        });

        Future future = (timeout, unit) -> {
            try {
                if (!latch.await(timeout, unit)) {
                    return false;
                }
            } catch (InterruptedException ex) {
                fail(ex);
            }

            if (testError[0] != null) {
                if (testError[0] instanceof Error) {
                    throw (Error)testError[0];
                } else if (testError[0] instanceof RuntimeException) {
                    throw (RuntimeException)testError[0];
                } else {
                    fail(testError[0].getCause());
                }
            }

            return true;
        };

        return future;
    }

    public static void runAndWait(Runnable... runnables) {
        runAndWait(false, runnables);
    }

    public static void runAndWait(boolean delay, Runnable... runnables) {
        List<Future> futures = new ArrayList(runnables.length);
        int i = 0;
        CountDownLatch delayLatch = delay ? new CountDownLatch(1) : null;
        for (Runnable r : runnables) {
            futures.add(submit(r, delayLatch));
        }
        if (delayLatch != null) {
            delayLatch.countDown();
        }

        int count = TIMEOUT / 100;
        while (!futures.isEmpty() && count-- > 0) {
            Iterator<Future> it = futures.iterator();
            while (it.hasNext()) {
                Future future = it.next();
                if (future.await(0, TimeUnit.MILLISECONDS)) {
                    it.remove();
                }
            }
            if (!futures.isEmpty()) {
                Util.sleep(100);
            }
        }

        if (!futures.isEmpty()) {
            fail("Exceeded timeout limit of " + TIMEOUT + " msec");
        }
    }

    public static ArrayList<String> createApplicationLaunchCommand(
            String testAppName,
            String testPldrName) throws IOException {

        return createApplicationLaunchCommand(testAppName, testPldrName, null);
    }

    public static ArrayList<String> createApplicationLaunchCommand(
            String testAppName,
            String testPldrName,
            String[] jvmArgs) throws IOException {

        final boolean isJar = testAppName.endsWith(".jar");

        /*
         * note: the "worker" properties are tied into build.gradle
         */
        final String workerJavaCmd = System.getProperty("worker.java.cmd");
        final String workerPatchModuleFile = System.getProperty("worker.patchmodule.file");
        final String workerClassPath = System.getProperty("worker.classpath.file");
        final Boolean workerDebug = Boolean.getBoolean("worker.debug");

        final ArrayList<String> cmd = new ArrayList<>(30);

        if (workerJavaCmd != null) {
            cmd.add(workerJavaCmd);
        } else {
            cmd.add("java");
        }

        if (workerPatchModuleFile != null) {
            cmd.add("@" + workerPatchModuleFile);
        } else {
            System.out.println("Warning: no worker.patchmodule passed to unit test");
        }

        // This is a "minimum" set, rather than the full @addExports
        cmd.add("--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED");
        cmd.add("--add-exports=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED");

        if (workerClassPath != null) {
            cmd.add("@" + workerClassPath);
        }

        if (testPldrName != null) {
            cmd.add("-Djavafx.preloader=" + testPldrName);
        }

        if (jvmArgs != null) {
            for (String arg : jvmArgs) {
                cmd.add(arg);
            }
        }

        if (isJar) {
            cmd.add("-jar");
        }
        cmd.add(testAppName);

        if (workerDebug) {
            System.err.println("Child cmd is");
            cmd.stream().forEach((s) -> {
                System.err.println(" " + s);
            });
            System.err.println("Child cmd: end");
        }

        return cmd;
    }

    /**
     * Launches an FX application, at the same time ensuring that it has been
     * actually launched within {@link #STARTUP_TIMEOUT} (15 seconds).
     * <p>
     * The application being started must call {@link CountdownLatch#countDown()} once to signal
     * its successful start (for example, by setting a handler for {@link javafx.stage.WindowEvent.WINDOW_SHOWN} event
     * on its primary Stage).
     *
     * @param startupLatch - a latch used to communicate successful start of the application
     * @param applicationClass - application to launch
     * @param args - command line arguments
     */
    public static <T extends Application> void launch (
            CountDownLatch startupLatch,
            Class<T> applicationClass,
            String... args) {
        launch(startupLatch, STARTUP_TIMEOUT, applicationClass, args);
    }

    /**
     * Launches an FX application, at the same time ensuring that it has been
     * actually launched within the specified time.
     * <p>
     * The application being started must call {@link java.util.concurrent.CountdownLatch#countDown()} once to signal
     * its successful start (for example, by setting a handler for {@link javafx.stage.WindowEvent.WINDOW_SHOWN} event
     * on its primary Stage).
     *
     * @param startupLatch - a latch used to communicate successful start of the application
     * @param timeoutSeconds - timeout in seconds after which the test fails
     * @param applicationClass - application to launch
     * @param args - command line arguments
     */
    public static <T extends Application> void launch (
            CountDownLatch startupLatch,
            int timeoutSeconds,
            Class<T> applicationClass,
            String... args) {

        new Thread(() -> {
            Application.launch(applicationClass, args);
        }).start();

        String msg = "Failed to launch FX application " + applicationClass + " within " + timeoutSeconds + " sec.";
        try {
            Assertions.assertTrue(startupLatch.await(timeoutSeconds, TimeUnit.SECONDS), msg);
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    /**
     * Starts the JavaFX runtime, invoking the specified Runnable on the JavaFX application thread.
     * This Runnable must call {@link java.util.concurrent.CountDownLatch#countDown()} once to signal
     * its successful start, otherwise an exception will be thrown when no such signal is received
     * within {@link #STARTUP_TIMEOUT} (15 seconds).
     *
     * @param startupLatch - a latch used to communicate successful start of the application
     * @param r - code to invoke on the application thread.
     */
    public static void startup(CountDownLatch startupLatch, Runnable r) {
        Platform.startup(r);
        try {
            String msg = "Timeout waiting for FX runtime to start";
            Assertions.assertTrue(startupLatch.await(STARTUP_TIMEOUT, TimeUnit.SECONDS), msg);
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    /**
     * This synchronous method first hides all the open {@code Window}s in the platform thread,
     * then invokes {@link Platform.exit()}.
     */
    public static void shutdown() {
        runAndWait(() -> {
            List.
                copyOf(Window.getWindows()).
                forEach(Window::hide);
            Platform.exit();
        });
    }

    /**
     * Calls CountDownLatch.await() with the specified timeout (in seconds).
     * Throws an exception if await() returns false or the process gets interrupted.
     */
    public static void waitForLatch(CountDownLatch latch, int seconds, String msg) {
        try {
            Assertions.assertTrue(latch.await(seconds, TimeUnit.SECONDS), "Timeout: " + msg);
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    /**
     * Makes double click of the mouse left button.
     */
    public static void doubleClick(Robot robot) {
        runAndWait(() -> {
            robot.mouseClick(MouseButton.PRIMARY);
        });
        sleep(50);
        runAndWait(() -> {
            robot.mouseClick(MouseButton.PRIMARY);
        });
    }

    /**
     * Moves the cursor outside of the Stage to avoid it interfering with Robot tests.
     * The cursor is moved to a point close to the lower right corner of the primary screen,
     * avoiding any areas occupied by dock, tray, or Active Corners.
     * <p>
     * This method can be called from any thread.
     */
    public static void parkCursor(Robot robot) {
        Runnable park = () -> {
            Rectangle2D r = Screen.getPrimary().getVisualBounds();
            double activeCornersMargin = 5.0;
            double x = r.getMaxX() - activeCornersMargin;
            double y = r.getMaxY() - activeCornersMargin;
            robot.mouseMove(x, y);
        };

        if (Platform.isFxApplicationThread()) {
            park.run();
        } else {
            runAndWait(park);
        }
    }

    /**
     * Triggers and waits for 10 pulses to complete in the specified scene.
     */
    public static void waitForIdle(Scene scene) {
        waitForIdle(scene, 10);
    }

    /**
     * Triggers and waits for specified number of pulses (pulseCount)
     * to complete in the specified scene.
     */
    public static void waitForIdle(Scene scene, int pulseCount) {
        CountDownLatch latch = new CountDownLatch(pulseCount);
        Runnable pulseListener = () -> {
            latch.countDown();
            Platform.requestNextPulse();
        };

        runAndWait(() -> {
            scene.addPostLayoutPulseListener(pulseListener);
        });

        try {
            Platform.requestNextPulse();
            waitForLatch(latch, TIMEOUT, "Timeout waiting for post layout pulse");
        } finally {
            runAndWait(() -> {
                scene.removePostLayoutPulseListener(pulseListener);
            });
        }
    }

    /** returns true if scaleX of the specified Node is not integer */
    public static boolean isFractionalScaleX(Node n) {
        double scale = n.getScene().getWindow().getRenderScaleX();
        return isFractional(scale);
    }

    private static boolean isFractional(double x) {
        return x != Math.rint(x);
    }

    /**
     * Returns the tolerance which should be used when comparing values,
     * when {@link Region#isSnapToPixel()} returns true and the scale can
     * be determined from the region's parent {@code Window}.
     * This amount equals to half of screen pixel converted to logical coordinates.
     * When scale cannot be determined it is assumed to be 0.5.
     * Otherwise, returns 0.0.
     *
     * @param r the region in question
     * @return the tolerance value
     */
    public static double getTolerance(Region r) {
        if (r.isSnapToPixel()) {
            Scene scene = r.getScene();
            if (scene != null) {
                Window win = scene.getWindow();
                if (win != null) {
                    // x and y usually have the same scale, so we'll use x
                    double scale = win.getRenderScaleX();
                    // distance between pixels in the local (unscaled) coordinates is (1 / scale)
                    return 0.5 / scale;
                }
            }
            // when the scale cannot be determited
            return 0.5;
        }
        return 0.0;
    }


    /**
     * Checks if the system is running Linux with the Wayland server.
     *
     * @return true if running on Wayland, false otherwise
     */
    public static boolean isOnWayland() {
        if (!PlatformUtil.isLinux()) return false;

        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        return waylandDisplay != null && !waylandDisplay.isEmpty();
    }
}
