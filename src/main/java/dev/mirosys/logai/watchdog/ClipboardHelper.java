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
		// Der Nutzer hat etwas anderes kopiert. Nichts zu tun.
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
