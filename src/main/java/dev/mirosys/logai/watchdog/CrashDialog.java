package dev.mirosys.logai.watchdog;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
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
 * Das Fenster, das nach einem Absturz erscheint. Es bleibt offen, bis der Nutzer es
 * schließt - auf allen Plattformen, damit die Zwischenablage bis dahin gültig bleibt.
 */
public final class CrashDialog {
	/** Sperrfrist gegen versehentliche Tastendruecke direkt nach dem Absturz. */
	private static final int ARM_DELAY_MILLIS = 750;

	static {
		// Verhindert, dass der Watcher unter macOS ein Dock-Icon mit falschem Namen bekommt.
		System.setProperty("apple.awt.application.name", "LogAI");
	}

	private CrashDialog() {
	}

	/** Was der Nutzer im Fenster angekreuzt hat. */
	public record Choice(boolean alwaysAuto, boolean restart) {
		static final Choice NOTHING = new Choice(false, false);
	}

	public static Choice show(WatchSession session, CrashEvidence evidence, Path report,
			LocalDateTime crashedAt, boolean copiedAsFile, boolean autoOpened, boolean canRestart) {
		if (GraphicsEnvironment.isHeadless()) {
			// Ohne Bildschirm gibt es nichts anzuzeigen, der Bericht liegt trotzdem auf der Platte.
			System.out.println("LogAI: crash report written to " + report);
			return Choice.NOTHING;
		}

		Choice[] choice = { Choice.NOTHING };
		CountDownLatch closed = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			try {
				buildDialog(session, evidence, report, crashedAt, copiedAsFile, autoOpened, canRestart,
						choice, closed);
			} catch (Throwable failure) {
				// Kein Fenster, kein Warten: der Bericht liegt trotzdem auf der Platte.
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
			LocalDateTime crashedAt, boolean copiedAsFile, boolean autoOpened, boolean canRestart,
			Choice[] choice, CountDownLatch closed) {
		applySystemLookAndFeel();

		String ai = session.provider.displayName();
		String stamp = ReportBuilder.HUMAN_STAMP.format(crashedAt);

		JDialog dialog = new JDialog((java.awt.Frame) null,
				"LogAI - " + titleWord(evidence.shutdownKind()) + " " + stamp, false);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(true);
		applyIcon(dialog);

		JPanel content = new JPanel(new BorderLayout(0, 14));
		content.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
		content.add(new JLabel("<html><body style='width: 380px'>"
				+ escape(message(ai, evidence.shutdownKind(), copiedAsFile, autoOpened))
				// Nicht jedes harte Schliessen ist ein Problem - manchmal will man einfach weg.
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
			// Bei einem Absturz kurz nach dem Start würde ein Neustart nur in einer
			// Schleife enden, und ohne aufgezeichnete Startzeile geht es ohnehin nicht.
			restart.setEnabled(false);
			restart.setText("Restart Minecraft (not available for this session)");
		}

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		JButton open = new JButton("Open " + ai + " in Browser");
		JButton copyOnly = new JButton("Copy only");
		JButton ignore = new JButton("Ignore");

		Runnable finish = () -> {
			choice[0] = new Choice(always.isSelected(), restart.isEnabled() && restart.isSelected());
			dialog.dispose();
		};

		open.addActionListener(event -> {
			// Der Bericht liegt bereits in der Zwischenablage, hier fehlt nur noch der Browser.
			openBrowser(session.provider.newChatUrl());
			finish.run();
		});
		copyOnly.addActionListener(event -> finish.run());
		ignore.addActionListener(event -> finish.run());
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				finish.run();
			}
		});

		if (autoOpened) {
			// Der Browser ist schon offen, es gibt nichts mehr zu entscheiden.
			buttons.add(copyOnly);
		} else {
			buttons.add(ignore);
			buttons.add(copyOnly);
			buttons.add(open);
			dialog.getRootPane().setDefaultButton(open);
		}

		// Das Fenster erscheint mitten im Spielen und reisst sich den Fokus. Wer in dem
		// Moment noch eine Taste gedrueckt haelt, wuerde sonst ungefragt den Standardbutton
		// ausloesen - also erst nach einer kurzen Schrecksekunde annehmen.
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
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				closed.countDown();
			}
		});
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

	private static String message(String ai, ShutdownKind kind, boolean copiedAsFile,
			boolean autoOpened) {
		String opening = switch (kind) {
			case CRASH -> "Minecraft just crashed. ";
			case ALT_F4 -> "Minecraft was closed with Alt+F4. ";
			case WINDOW_CLOSE -> "Minecraft was closed from outside the game. ";
			case QUIT -> "Minecraft was closed normally, nothing went wrong. ";
		};

		// Bei einem normalen Beenden gibt es keine Ursache zu finden, nur ein Log zu sichten.
		String job = kind == ShutdownKind.QUIT
				? "look through the log"
				: "analyse the cause";

		String middle;

		if (autoOpened) {
			middle = ai + " has been opened in your browser and the log file is on your clipboard, "
					+ "just paste it into the chat and send as is.";
		} else if (copiedAsFile) {
			middle = "Open " + ai + " in your browser and let it " + job + ". The log file is "
					+ "on your clipboard, just paste it into the chat and send as is.";
		} else {
			middle = "Open " + ai + " in your browser and let it " + job + ". This system did "
					+ "not allow copying the file itself, so the log was copied as plain text "
					+ "instead, just paste it into the chat and send as is.";
		}

		return opening + middle;
	}

	/** Das Wort in der Titelzeile - "crashed" wäre bei drei von vier Fällen gelogen. */
	private static String titleWord(ShutdownKind kind) {
		return switch (kind) {
			case CRASH -> "crashed";
			case ALT_F4, WINDOW_CLOSE -> "force-closed";
			case QUIT -> "closed";
		};
	}

	public static void openBrowser(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				return;
			}
		} catch (Exception ignored) {
			// Fällt unten auf den Kommandozeilen-Weg zurück.
		}

		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		try {
			if (os.contains("win")) {
				new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
			} else if (os.contains("mac")) {
				new ProcessBuilder("open", url).start();
			} else {
				new ProcessBuilder("xdg-open", url).start();
			}
		} catch (Exception ignored) {
			// Mehr können wir an dieser Stelle nicht tun.
		}
	}

	private static void applySystemLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// Dann eben das Standard-Look-and-Feel.
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
			// Ohne Icon sieht das Fenster nur etwas nackter aus.
		}
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
