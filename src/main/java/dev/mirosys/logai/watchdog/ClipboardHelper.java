package dev.mirosys.logai.watchdog;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Legt den Bericht als <em>Datei</em> in die Zwischenablage, sodass ein einzelnes Strg+V
 * im Chat einen Datei-Anhang erzeugt.
 *
 * <p>Wichtig: unter Linux gehört der Zwischenablage-Inhalt dem besitzenden Prozess. Der
 * Watcher darf deshalb erst beendet werden, wenn der Nutzer das Fenster schließt.
 */
public final class ClipboardHelper implements ClipboardOwner {
	/** Zählt herunter, sobald ein anderes Programm die Zwischenablage übernimmt. */
	private static final java.util.concurrent.CountDownLatch TAKEN =
			new java.util.concurrent.CountDownLatch(1);

	/** Wie lange der Watcher unter Linux still im Hintergrund wartet. */
	private static final long HOLD_MINUTES = 10;

	private ClipboardHelper() {
	}

	/**
	 * @return {@code true}, wenn die Datei selbst kopiert werden konnte, {@code false},
	 *         wenn auf reinen Text ausgewichen wurde.
	 */
	public static boolean copyAsFile(Path report) {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		try {
			clipboard.setContents(new FileTransferable(report.toFile()), new ClipboardHelper());
			return true;
		} catch (RuntimeException e) {
			return copyAsText(report);
		}
	}

	public static boolean copyAsText(Path report) {
		try {
			String content = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
			Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new StringSelection(content), new ClipboardHelper());
			return false;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		// Etwas anderes wurde kopiert - oder der Nutzer hat eingefügt und der Inhalt ist
		// angekommen. Ab hier muss dieser Prozess nichts mehr festhalten.
		TAKEN.countDown();
	}

	/**
	 * Hält den Prozess am Leben, solange die Zwischenablage ihn dafür braucht.
	 *
	 * <p>Unter Linux gehört der Inhalt dem Prozess, der ihn hineingelegt hat: endet er,
	 * ist die Zwischenablage leer. Wenn kein Fenster offen bleibt, muss der Watcher
	 * deshalb still im Hintergrund warten. Windows und macOS legen den Inhalt selbst ab
	 * und brauchen das nicht.
	 */
	public static void holdWhileNeeded() {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);

		if (os.contains("win") || os.contains("mac")) {
			return;
		}

		try {
			TAKEN.await(HOLD_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private record FileTransferable(File file) implements Transferable {
		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] { DataFlavor.javaFileListFlavor };
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return DataFlavor.javaFileListFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
			if (!isDataFlavorSupported(flavor)) {
				throw new UnsupportedFlavorException(flavor);
			}

			return List.of(file);
		}
	}
}
