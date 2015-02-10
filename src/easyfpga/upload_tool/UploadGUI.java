/*
 *  This file is part of easyFPGA.
 *  Copyright 2013-2015 os-cillation GmbH
 *
 *  easyFPGA is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  easyFPGA is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with easyFPGA.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package easyfpga.upload_tool;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import easyfpga.communicator.DeviceDetector;
import easyfpga.communicator.FPGABinary;
import easyfpga.communicator.VirtualComPort;
import easyfpga.exceptions.CommunicationException;

/**
 * A simple Swing-GUI for uploading an FPGA binary. Used when the UploadTool main is called
 * without any arguments.
 */
public class UploadGUI extends JFrame implements ActionListener, Observer {

    private static final long serialVersionUID = 8922806574869281870L;

    /* constants */
    private final String WINDOW_TITLE = "easyFPGA Upload Tool";
    private final String OPEN_BUTTON_TEXT = "Open Binary";
    private final String CONF_BUTTON_TEXT = "Upload and Configure";
    private final String QUIT_BUTTON_TEXT = "Quit";
    private final String ALREADY_UPLOADED_MESSAGE = "Selected binary is already configured. "
                                                    + "Skipping upload.";
    private final String CANNOT_CLOSE_MESSAGE = "Please wait until current operation has finished";

    private final Dimension WINDOW_DIMESION_START = new Dimension(600, 200);
    private final Dimension WINDOW_DIMENSION_MIN = new Dimension(300, 100);
    private final int STATUS_BAR_HEIGHT = 20;
    private final int VERTICAL_SEPARATOR_HEIGHT = 7;

    private final long DETECT_SCHEDULE_MILLIS = 300;

    /* gui elements */
    private JProgressBar progressBar;
    private JButton openButton;
    private JButton configureButton;
    private JButton quitButton;
    private StatusBar statusBar;

    private FPGABinary binary;
    private String deviceName;
    private boolean blockTermination;

    /** Scheduler for periodic device detection */
    ScheduledExecutorService detectScheduler;

    public UploadGUI() {
        super();
        initView();
        scheduleDetectRunnable();
        blockTermination = false;
    }

    private void initView() {
        /* frame */
        setTitle(WINDOW_TITLE);
        setSize(WINDOW_DIMESION_START);
        setMinimumSize(WINDOW_DIMENSION_MIN);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setupCloseBehavior();

        /* icon */
        ImageIcon icon = new ImageIcon(getClass().getResource("icon.png"));
        setIconImage(icon.getImage());

        /* setup panels */
        Container rootPanel = getContentPane();
        BoxLayout layout = new BoxLayout(rootPanel, BoxLayout.Y_AXIS);
        rootPanel.setLayout(layout);
        JPanel progressPanel = new JPanel(new GridLayout(1, 1, 10, 10));
        JPanel buttonsPanel = new JPanel(new GridLayout(1,3, 10, 10));

        /* insert sub panels */
        rootPanel.add(createVerticalSeparator());
        rootPanel.add(progressPanel);
        rootPanel.add(createVerticalSeparator());
        rootPanel.add(buttonsPanel);
        rootPanel.add(createVerticalSeparator());

        /* status bar */
        statusBar = new StatusBar();
        statusBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, STATUS_BAR_HEIGHT));
        rootPanel.add(statusBar);

        /* progress bar */
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setBorder(BorderFactory.createLineBorder(Color.black)); //DBG!
        progressPanel.add(progressBar);

        /* buttons */
        openButton = createButton(OPEN_BUTTON_TEXT);
        configureButton = createButton(CONF_BUTTON_TEXT);
        quitButton = createButton(QUIT_BUTTON_TEXT);
        configureButton.setEnabled(false);

        buttonsPanel.add(openButton);
        buttonsPanel.add(configureButton);
        buttonsPanel.add(quitButton);
    }

    private JButton createButton(String lable) {
        JButton b = new JButton(lable);
        b.addActionListener(this);
        return b;
    }

    private Component createVerticalSeparator() {
        Component sep = Box.createRigidArea(new Dimension(0,VERTICAL_SEPARATOR_HEIGHT));
        return sep;
    }

    private void scheduleDetectRunnable() {

        final DeviceDetector dd = new DeviceDetector();
        dd.addObserver(this);

        detectScheduler = Executors.newScheduledThreadPool(1);
        detectScheduler.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                deviceName = dd.findDevice();
                statusBar.setDetectedDevice(deviceName);
            }
        }, 0, DETECT_SCHEDULE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void setupCloseBehavior() {
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                if (blockTermination) {
                    JOptionPane.showMessageDialog(null, CANNOT_CLOSE_MESSAGE, WINDOW_TITLE,
                                                    JOptionPane.ERROR_MESSAGE);
                }
                else {
                    terminate();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    private void terminate() {
        detectScheduler.shutdownNow();
        try {
            detectScheduler.awaitTermination(DETECT_SCHEDULE_MILLIS * 2, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.dispose();
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        /* open button clicked */
        if (e.getSource() == openButton) {
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.addChoosableFileFilter(new BinaryFileFilter());
            fileChooser.setFileHidingEnabled(false);

            int openReturnValue = fileChooser.showOpenDialog(this);

            if (openReturnValue == JFileChooser.APPROVE_OPTION) {
                File binaryFile = fileChooser.getSelectedFile();
                try {
                    String path = binaryFile.getCanonicalPath();
                    binary = new FPGABinary(path);
                    binary.loadFile();
                    binary.addObserver(this);
                    configureButton.setEnabled(true);
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        /* configure button clicked */
        else if (e.getSource() == configureButton) {
            UploadConfigureTask task = new UploadConfigureTask();
            task.execute();
        }

        /* quit button clicked */
        else if (e.getSource() == quitButton) {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        }
    }

    @Override
    public void update(Observable obs, Object notificationObject) {

        if (obs instanceof FPGABinary) {
            /* binary passes integers while uploading */
            if (notificationObject instanceof Integer) {
                Integer progress = (Integer) notificationObject;
                progressBar.setValue(progress);
            }
            /* when skipping upload binary hands over a true boolean */
            if (notificationObject instanceof Boolean) {
                if ((Boolean) notificationObject) {
                progressBar.setValue(100);
                JOptionPane.showMessageDialog(this, ALREADY_UPLOADED_MESSAGE, WINDOW_TITLE,
                                                JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
        else if (obs instanceof DeviceDetector) {
            /* if a certain board is configuring, device detector passes its device name */
            if (notificationObject instanceof String) {
                statusBar.showCurrentlyConfiguringDevice((String) notificationObject);
            }
        }
    }

    class UploadConfigureTask extends SwingWorker<Void, Void> {

        private VirtualComPort vcp;

        @Override
        protected Void doInBackground() throws Exception {

            /* disable all buttons */
            openButton.setEnabled(false);
            configureButton.setEnabled(false);
            quitButton.setEnabled(false);

            /* stop detect scheduler */
            detectScheduler.shutdown();
            try {
                detectScheduler.awaitTermination(DETECT_SCHEDULE_MILLIS * 2, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            /* avoid closing during configuration */
            blockTermination = true;

            /* open vcp */
            vcp = new VirtualComPort(deviceName);
            try {
                vcp.open();
            }
            catch (CommunicationException e) {
                statusBar.printError("Failed to open device");
                return null;
            }

            /* upload */
            statusBar.printMessage("Uploading binary ...");
            if (!binary.upload(vcp)) {
                progressBar.setValue(0);
                statusBar.printError("Failed to upload binary");
                return null;
            }

            /* configure */
            statusBar.printMessage("Configuring FPGA ...");
            progressBar.setIndeterminate(true);
            if (binary.configureFPGA(vcp)) {
                statusBar.printNice("Upload and configuration successful");
            }
            else {
                statusBar.printError("Failed to configure FPGA. Invalid Binary?");
                progressBar.setValue(0);
            }
            progressBar.setIndeterminate(false);
            return null;
        }

        @Override
        protected void done() {
            /* enable buttons */
            openButton.setEnabled(true);
            configureButton.setEnabled(true);
            quitButton.setEnabled(true);

            /* enable detection and window closing */
            vcp.close();
            blockTermination = false;
            scheduleDetectRunnable();
        }
    }
}
