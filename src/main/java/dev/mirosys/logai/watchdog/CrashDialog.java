package dev.mirosys.logai.watchdog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * The window that appears after the game ended. It stays open until the user closes it -
 * on all platforms, which is also what keeps the clipboard valid on Linux.
 */
public final class CrashDialog {
	/** Buttons stay disabled this long, so a key still held from the game hits nothing. */
	private static final int ARM_DELAY_MILLIS = 750;

	static {
		// Otherwise the watcher shows up in the macOS dock under the wrong name.
		System.setProperty("apple.awt.application.name", "LogAI");
	}

	private CrashDialog() {
	}

	/** What the user ticked in the window. */
	public record Choice(boolean alwaysAuto, boolean restart) {
		static final Choice NOTHING = new Choice(false, false);
	}

	public static Choice show(WatchSession session, CrashEvidence evidence, Path report,
			LocalDateTime crashedAt, boolean copiedAsFile, boolean canRestart) {
		if (GraphicsEnvironment.isHeadless()) {
			// No screen, nothing to show. The report is on disk regardless.
			System.out.println("LogAI: crash report written to " + report);
			return Choice.NOTHING;
		}

		Choice[] choice = { Choice.NOTHING };
		CountDownLatch closed = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			try {
				buildDialog(session, evidence, report, crashedAt, copiedAsFile, canRestart, choice, closed);
			} catch (Throwable failure) {
				// No window, no waiting: the report is on disk regardless.
				System.err.println("LogAI: could not show the crash dialog: " + failure);
				closed.countDown();
			}
		});

		try {
			closed.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return choice[0];
	}

	private static void buildDialog(WatchSession session, CrashEvidence evidence, Path report,
			LocalDateTime crashedAt, boolean copiedAsFile, boolean canRestart, Choice[] choice,
			CountDownLatch closed) {
		applySystemLookAndFeel();

		String ai = session.provider.displayName();
		ShutdownKind kind = evidence.shutdownKind();

		JDialog dialog = new JDialog((Frame) null,
				"LogAI - " + titleWord(kind) + " " + ReportBuilder.HUMAN_STAMP.format(crashedAt), false);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(true);
		applyIcon(dialog);

		JPanel content = new JPanel(new BorderLayout(0, 14));
		content.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
		content.add(new JLabel("<html><body style='width: 380px'>"
				+ escape(message(ai, kind, copiedAsFile))
				// Not every force-close is a problem; sometimes you just want out.
				+ "<br><br>" + escape("If you closed the game on purpose and nothing was wrong, "
						+ "you can simply ignore this window.")
				+ "<br><br><span style='color:#666'>Saved to " + escape(report.toString())
				+ "</span></body></html>"), BorderLayout.CENTER);

		JCheckBox always = new JCheckBox("Always copy and open automatically, don't ask again");
		always.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		JCheckBox restart = new JCheckBox("Restart Minecraft when this window closes",
				canRestart && session.autoRestart);
		restart.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		if (!canRestart) {
			restart.setEnabled(false);
			restart.setText("Restart Minecraft (not available for this session)");
		}

		JButton open = new JButton("Open " + ai + " in Browser");
		JButton copyOnly = new JButton("Copy only");
		JButton ignore = new JButton("Ignore");

		Runnable finish = () -> {
			choice[0] = new Choice(always.isSelected(), restart.isEnabled() && restart.isSelected());
			dialog.dispose();
		};

		open.addActionListener(event -> {
			// Close first, then open: this window is always on top and would take the
			// focus away from the browser otherwise.
			finish.run();
			UriOpener.open(session.provider.newChatUrl());
		});
		copyOnly.addActionListener(event -> finish.run());
		ignore.addActionListener(event -> finish.run());
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				finish.run();
			}

			@Override
			public void windowClosed(WindowEvent event) {
				closed.countDown();
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		buttons.add(ignore);
		buttons.add(copyOnly);
		buttons.add(open);
		dialog.getRootPane().setDefaultButton(open);

		// The window pops up mid-game and grabs the focus. A key still held down would
		// otherwise trigger the default button before anyone has read a word.
		armAfterDelay(open, copyOnly, ignore);

		JPanel bottom = new JPanel();
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
		bottom.add(restart);
		bottom.add(always);
		bottom.add(Box.createVerticalStrut(10));
		bottom.add(buttons);
		content.add(bottom, BorderLayout.SOUTH);

		dialog.setContentPane(content);
		dialog.pack();
		dialog.setMinimumSize(new Dimension(dialog.getWidth(), dialog.getHeight()));
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
		dialog.toFront();
	}

	private static void armAfterDelay(JButton... buttons) {
		for (JButton button : buttons) {
			button.setEnabled(false);
		}

		Timer arm = new Timer(ARM_DELAY_MILLIS, event -> {
			for (JButton button : buttons) {
				button.setEnabled(true);
			}
		});
		arm.setRepeats(false);
		arm.start();
	}

	private static String message(String ai, ShutdownKind kind, boolean copiedAsFile) {
		String opening = switch (kind) {
			case CRASH -> "Minecraft just crashed. ";
			case ALT_F4 -> "Minecraft was closed with Alt+F4. ";
			case WINDOW_CLOSE -> "Minecraft was closed from outside the game. ";
			case QUIT -> "Minecraft was closed normally, nothing went wrong. ";
		};

		// After a normal quit there is no cause to find, just a log to look through.
		String job = kind == ShutdownKind.QUIT ? "look through the log" : "analyse the cause";

		String clipboard = copiedAsFile
				? "The log file is on your clipboard, just paste it into the chat and send as is."
				: "This system did not allow copying the file itself, so the log was copied as "
						+ "plain text instead, just paste it into the chat and send as is.";

		return opening + "Open " + ai + " in your browser and let it " + job + ". " + clipboard;
	}

	/** The word in the title bar; "crashed" would be a lie for three of the four cases. */
	private static String titleWord(ShutdownKind kind) {
		return switch (kind) {
			case CRASH -> "crashed";
			case ALT_F4, WINDOW_CLOSE -> "force-closed";
			case QUIT -> "closed";
		};
	}

	private static void applySystemLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// The default look and feel will do.
		}
	}

	private static void applyIcon(JDialog dialog) {
		try (InputStream stream = CrashDialog.class.getResourceAsStream("/assets/logai/icon.png")) {
			if (stream != null) {
				Image image = ImageIO.read(stream);

				if (image != null) {
					dialog.setIconImage(image);
				}
			}
		} catch (Exception ignored) {
			// A window without an icon just looks a little plain.
		}
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
