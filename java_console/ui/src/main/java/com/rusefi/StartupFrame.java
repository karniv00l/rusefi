package com.rusefi;

import com.devexperts.logging.Logging;
import com.rusefi.autodetect.PortDetector;
import com.rusefi.autodetect.SerialAutoChecker;
import com.rusefi.core.io.BundleUtil;
import com.rusefi.core.ui.AutoupdateUtil;
import com.rusefi.io.LinkManager;
import com.rusefi.io.serial.BaudRateHolder;
import com.rusefi.maintenance.DriverInstall;
import com.rusefi.maintenance.ExecHelper;
import com.rusefi.maintenance.FirmwareFlasher;
import com.rusefi.maintenance.ProgramSelector;
import com.rusefi.ui.PcanConnectorUI;
import com.rusefi.ui.util.HorizontalLine;
import com.rusefi.ui.util.URLLabel;
import com.rusefi.ui.util.UiUtils;
import com.rusefi.util.IoUtils;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.putgemin.VerticalFlowLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.devexperts.logging.Logging.getLogging;
import static com.rusefi.core.preferences.storage.PersistentConfiguration.getConfig;
import static com.rusefi.ui.util.UiUtils.*;
import static javax.swing.JOptionPane.YES_NO_OPTION;

/**
 * This frame is used on startup to select the port we would be using
 *
 * @author Andrey Belomutskiy
 * <p/>
 * 2/14/14
 * @see SimulatorHelper
 * @see FirmwareFlasher
 */
public class StartupFrame {
    private static final Logging log = getLogging(Launcher.class);

    public static final String LOGO_PATH = "/com/rusefi/";
    private static final String LOGO = LOGO_PATH + "logo.png";
    public static final String LINK_TEXT = "rusEFI (c) 2012-2022";
    private static final String URI = "http://rusefi.com/?java_console";
    // private static final int RUSEFI_ORANGE = 0xff7d03;

    private final JFrame frame;
    private final JPanel connectPanel = new JPanel(new FlowLayout());
    // todo: move this line to the connectPanel
    private final JComboBox<String> comboPorts = new JComboBox<>();
    private final JPanel leftPanel = new JPanel(new VerticalFlowLayout());

    private final JPanel realHardwarePanel = new JPanel(new MigLayout());
    private final JPanel miscPanel = new JPanel(new MigLayout()) {
        @Override
        public Dimension getPreferredSize() {
            // want miscPanel and realHardwarePanel to be the same width
            Dimension size = super.getPreferredSize();
            return new Dimension(Math.max(size.width, realHardwarePanel.getPreferredSize().width), size.height);
        }
    };
    /**
     * this flag tells us if we are closing the startup frame in order to proceed with console start or if we are
     * closing the application.
     */
    private boolean isProceeding;
    private final JLabel noPortsMessage = new JLabel("<html>No ports found!<br>Confirm blue LED is blinking</html>");

    public StartupFrame() {
//        AudioPlayback.start();
        String title = "rusEFI console version " + Launcher.CONSOLE_VERSION;
        frame = new JFrame(appendBundleName(title));
        frame.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent ev) {
                if (!isProceeding) {
                    getConfig().save();
                    IoUtils.exit("windowClosed", 0);
                }
            }
        });
        AutoupdateUtil.setAppIcon(frame);
    }

    @NotNull
    public static String appendBundleName(String title) {
        String bundleName = BundleUtil.readBundleFullNameNotNull();
        return title + " " + bundleName;
    }

    public void chooseSerialPort() {
        realHardwarePanel.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.darkGray), "Real stm32"));
        miscPanel.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.darkGray), "Miscellaneous"));

        if (FileLog.isWindows()) {
            setToolTip(comboPorts, "Use 'Device Manager' icon above to launch Device Manager",
                    "In 'Ports' section look for ",
                    "'STMicroelectronics Virtual COM Port' for USB port",
                    "'USB Serial Port' for TTL port");
        }

        connectPanel.add(comboPorts);
        final JComboBox<String> comboSpeeds = createSpeedCombo();
        comboSpeeds.setToolTipText("For 'STMicroelectronics Virtual COM Port' device any speed setting would work the same");
        connectPanel.add(comboSpeeds);

        final JButton connectButton = new JButton("Connect", new ImageIcon(getClass().getResource("/com/rusefi/connect48.png")));
        //connectButton.setBackground(new Color(RUSEFI_ORANGE)); // custom orange
        setToolTip(connectButton, "Connect to real hardware");
        connectPanel.add(connectButton);
        connectPanel.setVisible(false);

        frame.getRootPane().setDefaultButton(connectButton);
        connectButton.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    connectButtonAction(comboSpeeds);
                }
            }
        });

        connectButton.addActionListener(e -> connectButtonAction(comboSpeeds));

        leftPanel.add(realHardwarePanel);
        leftPanel.add(miscPanel);

        if (FileLog.isWindows()) {
            JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
            topButtons.add(createShowDeviceManagerButton());
            topButtons.add(DriverInstall.createButton());
            topButtons.add(createPcanConnectorButton());
            realHardwarePanel.add(topButtons, "right, wrap");
        }
        realHardwarePanel.add(connectPanel, "right, wrap");
        realHardwarePanel.add(noPortsMessage, "right, wrap");
        installMessage(noPortsMessage, "Check you cables. Check your drivers. Do you want to start simulator maybe?");

        ProgramSelector selector = new ProgramSelector(comboPorts);

        if (FileLog.isWindows()) {
            realHardwarePanel.add(new HorizontalLine(), "right, wrap");

            realHardwarePanel.add(selector.getControl(), "right, wrap");

            // for F7 builds we just build one file at the moment
//            realHardwarePanel.add(new FirmwareFlasher(FirmwareFlasher.IMAGE_FILE, "ST-LINK Program Firmware", "Default firmware version for most users").getButton());
            if (new File(FirmwareFlasher.IMAGE_NO_ASSERTS_FILE).exists()) {
                // 407 build
                FirmwareFlasher firmwareFlasher = new FirmwareFlasher(FirmwareFlasher.IMAGE_NO_ASSERTS_FILE, "ST-LINK Program Firmware/NoAsserts", "Please only use this version if you know that you need this version");
                realHardwarePanel.add(firmwareFlasher.getButton(), "right, wrap");
            }
            JComponent updateHelp = ProgramSelector.createHelpButton();

            realHardwarePanel.add(updateHelp, "right, wrap");

            // st-link is pretty advanced use-case, real humans do not have st-link as of 2021
            //realHardwarePanel.add(new EraseChip().getButton(), "right, wrap");
        }

        SerialPortScanner.INSTANCE.addListener(currentHardware -> SwingUtilities.invokeLater(() -> {
            selector.apply(currentHardware);
            applyKnownPorts(currentHardware);
            frame.pack();
        }));

        final JButton buttonLogViewer = new JButton();
        buttonLogViewer.setText("Start " + LinkManager.LOG_VIEWER);
        buttonLogViewer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disposeFrameAndProceed();
                new ConsoleUI(LinkManager.LOG_VIEWER);
            }
        });

        miscPanel.add(buttonLogViewer, "wrap");
        miscPanel.add(new HorizontalLine(), "wrap");

        miscPanel.add(SimulatorHelper.createSimulatorComponent(this));

        JPanel rightPanel = new JPanel(new VerticalFlowLayout());

        if (BundleUtil.readBundleFullNameNotNull().contains("proteus_f7")) {
            String text = "WARNING: Proteus F7";
            URLLabel urlLabel = new URLLabel(text, "https://github.com/rusefi/rusefi/wiki/F7-requires-full-erase");
            Color originalColor = urlLabel.getForeground();
            new Timer(500, new ActionListener() {
                int counter;
                @Override
                public void actionPerformed(ActionEvent e) {
                    // URL color is hard-coded, let's blink isUnderlined attribute as second best option
                    urlLabel.setText(text, counter++ % 2 == 0);
                }
            }).start();
            rightPanel.add(urlLabel);
        }

        JLabel logo = createLogoLabel();
        if (logo != null)
            rightPanel.add(logo);
        rightPanel.add(new URLLabel(LINK_TEXT, URI));
        rightPanel.add(new JLabel("Version " + Launcher.CONSOLE_VERSION));

        JPanel content = new JPanel(new BorderLayout());
        content.add(leftPanel, BorderLayout.WEST);
        content.add(rightPanel, BorderLayout.EAST);
        frame.add(content);
        frame.pack();
        setFrameIcon(frame);
        frame.setVisible(true);
        UiUtils.centerWindow(frame);

        KeyListener hwTestEasterEgg = functionalTestEasterEgg();

        for (Component component : getAllComponents(frame)) {
            component.addKeyListener(hwTestEasterEgg);
        }
    }

    private void applyKnownPorts(SerialPortScanner.AvailableHardware currentHardware) {
        List<String> ports = currentHardware.getKnownPorts();
            log.info("Rendering available ports: " + ports);
            connectPanel.setVisible(!ports.isEmpty());
            noPortsMessage.setVisible(ports.isEmpty());

            applyPortSelectionToUIcontrol(ports);
            UiUtils.trueLayout(connectPanel);
    }

    public static void setFrameIcon(Frame frame) {
        ImageIcon icon = getBundleIcon();
        if (icon != null)
            frame.setIconImage(icon.getImage());
    }

    public static JLabel createLogoLabel() {
        ImageIcon logoIcon = getBundleIcon();
        if (logoIcon == null)
            return null;
        JLabel logo = new JLabel(logoIcon);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        URLLabel.addUrlAction(logo, URLLabel.createUri(URI));
        logo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return logo;
    }

    @Nullable
    private static ImageIcon getBundleIcon() {
        String bundle = BundleUtil.readBundleFullNameNotNull();
        String logoName;
        // these should be about 213px wide
        if (bundle.contains("proteus")) {
            logoName = LOGO_PATH + "logo_proteus.png";
        } else if (bundle.contains("_alphax")) {
            logoName = LOGO_PATH + "logo_alphax.png";
        } else if (bundle.contains("_mre")) {
            logoName = LOGO_PATH + "logo_mre.png";
        } else {
            logoName = LOGO;
        }
        return AutoupdateUtil.loadIcon(logoName);
    }

    private void connectButtonAction(JComboBox<String> comboSpeeds) {
        BaudRateHolder.INSTANCE.baudRate = Integer.parseInt((String) comboSpeeds.getSelectedItem());
        String selectedPort = comboPorts.getSelectedItem().toString();
        if (SerialPortScanner.AUTO_SERIAL.equals(selectedPort)) {
            SerialAutoChecker.AutoDetectResult detectResult = PortDetector.autoDetectPort(StartupFrame.this.frame);
            String autoDetectedPort = detectResult == null ? null : detectResult.getSerialPort();
            if (autoDetectedPort == null)
                return;
            selectedPort = autoDetectedPort;
        }
        disposeFrameAndProceed();
        new ConsoleUI(selectedPort);
    }

    /**
     * Here we listen to keystrokes while console start-up frame is being displayed and if magic "test" word is typed
     * we launch a functional test on real hardware, same as Jenkins runs within continuous integration
     */
    @NotNull
    private KeyListener functionalTestEasterEgg() {
        return new KeyAdapter() {
            private final static String TEST = "test";
            private String recentKeyStrokes = "";

            @Override
            public void keyTyped(KeyEvent e) {
                recentKeyStrokes = recentKeyStrokes + e.getKeyChar();
                if (recentKeyStrokes.toLowerCase().endsWith(TEST) && showTestConfirmation()) {
                    runFunctionalHardwareTest();
                }
            }

            private boolean showTestConfirmation() {
                return JOptionPane.showConfirmDialog(StartupFrame.this.frame, "Want to run functional test? This would freeze UI for the duration of the test",
                        "Better do not run while connected to vehicle!!!", YES_NO_OPTION) == JOptionPane.YES_OPTION;
            }

            private void runFunctionalHardwareTest() {
                boolean isSuccess = RealHardwareTestLauncher.runHardwareTest();
                JOptionPane.showMessageDialog(null, "Function test passed: " + isSuccess + "\nSee log folder for details.");
            }
        };
    }

    private Component createPcanConnectorButton() {
        JButton button = new JButton("PCAN");
        button.setToolTipText("PCAN connector for TS");
        button.addActionListener(e -> PcanConnectorUI.show());
        return button;
    }

    private Component createShowDeviceManagerButton() {
        JButton showDeviceManager = new JButton(AutoupdateUtil.loadIcon("DeviceManager.png"));
        showDeviceManager.setMargin(new Insets(0, 0, 0, 0));
        showDeviceManager.setToolTipText("Show Device Manager");
        showDeviceManager.addActionListener(event -> {
            try {
                Runtime.getRuntime().exec(ExecHelper.getBatchCommand("devmgmt.msc"));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
        return showDeviceManager;
    }

    private void installMessage(JComponent component, String s) {
        component.setToolTipText(s);
    }

    public void disposeFrameAndProceed() {
        isProceeding = true;
        frame.dispose();
        SerialPortScanner.INSTANCE.stopTimer();
    }

    private void applyPortSelectionToUIcontrol(List<String> ports) {
        comboPorts.removeAllItems();
        for (final String port : ports)
            comboPorts.addItem(port);
        String defaultPort = getConfig().getRoot().getProperty(ConsoleUI.PORT_KEY);
        comboPorts.setSelectedItem(defaultPort);
        trueLayout(comboPorts);
    }

    private static JComboBox<String> createSpeedCombo() {
        JComboBox<String> combo = new JComboBox<>();
        String defaultSpeed = getConfig().getRoot().getProperty(ConsoleUI.SPEED_KEY, "115200");
        for (int speed : new int[]{9600, 14400, 19200, 38400, 57600, 115200, 460800, 921600})
            combo.addItem(Integer.toString(speed));
        combo.setSelectedItem(defaultSpeed);
        return combo;
    }
}
