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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;


/**
 * Statusbar for the upload tool GUI
 */
public class StatusBar extends JPanel {

    private static final long serialVersionUID = 8981970911699069306L;

    private final String DEVICE_INIT_MSG = "No board detected";
    private final String WELCOME_MSG = "easyFPGA upload tool";

    private final Color DEFAULT_COLOR = Color.black;
    private final Color NICE_COLOR = new Color(0x00CC00);
    private final Color WARN_COLOR = Color.orange;
    private final Color ERROR_COLOR = Color.red;

    private JPanel devicePanel;
    private JLabel deviceLabel;
    private JPanel messagePanel;
    private JLabel messageLabel;

    public StatusBar() {
        super(new GridBagLayout());
        initView();
    }

    public void setDetectedDevice(String deviceName) {
        if (deviceName != null) {
            deviceLabel.setText(deviceName);
            deviceLabel.setForeground(NICE_COLOR);
        }
        else {
            deviceLabel.setText(DEVICE_INIT_MSG);
            deviceLabel.setForeground(ERROR_COLOR);
        }
    }

    public void showCurrentlyConfiguringDevice(String deviceName) {
        deviceLabel.setText(deviceName);
        deviceLabel.setForeground(WARN_COLOR);
    }

    public void printMessage(String message) {
        messageLabel.setText(message);
        messageLabel.setForeground(DEFAULT_COLOR);
    }

    public void printNice(String message) {
        messageLabel.setText(message);
        messageLabel.setForeground(NICE_COLOR);
    }

    public void printError(String message) {
        messageLabel.setText(message);
        messageLabel.setForeground(ERROR_COLOR);
    }

    private void initView() {
        /* panels and layout */
        devicePanel = new JPanel();
        messagePanel = new JPanel();

        devicePanel.setBorder(BorderFactory.createLoweredBevelBorder());
        messagePanel.setBorder(BorderFactory.createLoweredBevelBorder());

        GridBagConstraints devConstr = new GridBagConstraints();
        devConstr.weightx = 0.2;
        devConstr.fill = GridBagConstraints.HORIZONTAL;
        add(devicePanel, devConstr);

        GridBagConstraints msgConstr = new GridBagConstraints();
        msgConstr.weightx = 0.8;
        msgConstr.fill = GridBagConstraints.HORIZONTAL;
        add(messagePanel, msgConstr);

        /* labels */
        deviceLabel = new JLabel(DEVICE_INIT_MSG);
        deviceLabel.setForeground(WARN_COLOR);
        devicePanel.add(deviceLabel);
        setDetectedDevice(null);

        messageLabel = new JLabel(WELCOME_MSG);
        messagePanel.add(messageLabel);
    }
}
