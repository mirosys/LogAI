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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Puts the report on the clipboard as a <em>file</em>, so a single Ctrl+V in a chat
 * attaches it instead of pasting a wall of text.
 *
 * <p>On Linux the clipboard content belongs to the process that set it, so this process
 * must stay alive until someone has taken it. See {@link #holdWhileNeeded()}.
 */
public final class ClipboardHelper implements ClipboardOwner {
	/** Counts down as soon as another program takes over the clipboard. */
	private static final CountDownLatch TAKEN = new CountDownLatch(1);

	/** How long the watcher waits in the background on Linux before giving up. */
	private static final long HOLD_MINUTES = 10;

	private ClipboardHelper() {
	}

	/**
	 * @return {@code true} if the file itself was copied, {@code false} if we had to fall
	 *         back to plain text
	 */
	public static boolean copyAsFile(Path report) {
		try {
			clipboard().setContents(new FileTransferable(report.toFile()), new ClipboardHelper());
			return true;
		} catch (RuntimeException e) {
			return copyAsText(report);
		}
	}

	public static boolean copyAsText(Path report) {
		try {
			String content = Files.readString(report, StandardCharsets.UTF_8);
			clipboard().setContents(new StringSelection(content), new ClipboardHelper());
		} catch (IOException | RuntimeException ignored) {
			// Nothing more to try.
		}

		return false;
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		// Something else was copied, or the paste went through. Either way this process
		// no longer needs to hold anything.
		TAKEN.countDown();
	}

	/**
	 * Keeps the process alive for as long as the clipboard needs it.
	 *
	 * <p>Windows and macOS store the content themselves; there is nothing to wait for.
	 * On Linux the content dies with the owning process, so when no dialog stays open the
	 * watcher has to wait quietly in the background instead.
	 */
	public static void holdWhileNeeded() {
		if (Os.current() != Os.LINUX) {
			return;
		}

		try {
			TAKEN.await(HOLD_MINUTES, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static Clipboard clipboard() {
		return Toolkit.getDefaultToolkit().getSystemClipboard();
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
